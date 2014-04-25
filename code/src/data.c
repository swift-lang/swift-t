/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */


#include <assert.h>
#include <inttypes.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <list_i.h>
#include <list_l.h>
#include <table.h>
#include <table_bp.h>
#include <table_lp.h>
#include <vint.h>

#include "adlb.h"
#include "adlb_types.h"
#include "data.h"
#include "data_cleanup.h"
#include "data_internal.h"
#include "data_structs.h"
#include "debug.h"
#include "multiset.h"
#include "refcount.h"
#include "sync.h"

/**
   Map from adlb_datum_id to adlb_datum
*/
static struct table_lp tds;

/**
   Map from "container,subscript" specifier to list of listening references.
 */
static struct table_bp container_references;

/**
   Map from "container,subscript" specifier to list of subscribers to that
   subscript.
 */
static struct table_bp container_ix_listeners;

/**
   Map from adlb_datum_id to int rank if locked
*/
static struct table_lp locked;

/**
   Number of ADLB servers
*/
static int servers = 1;

/**
   Unique datum id.  Note that 0 is ADLB_DATA_ID_NULL.
*/
static adlb_datum_id unique = -1;

/**
   When data_unique hits this, return an error- we have exhausted the
   longs. Defined in data_init()
 */
static adlb_datum_id last_id;

static adlb_data_code
datum_init_props(adlb_datum_id id, adlb_datum *d,
                 const adlb_create_props *props);

static adlb_data_code
datum_init_container(adlb_datum *d, adlb_data_type key_type,
                 adlb_data_type val_type);

static adlb_data_code
datum_init_multiset(adlb_datum *d, adlb_data_type val_type);

static adlb_data_code
xlb_data_close(adlb_datum_id id, adlb_datum *d, adlb_notif_ranks *notify);
static adlb_data_code datum_gc(adlb_datum_id id, adlb_datum* d,
           refcount_scavenge scav);

static adlb_data_code
insert_notifications(adlb_datum *d,
            adlb_datum_id container_id, adlb_subscript subscript,
            adlb_datum_storage *inserted_value,
            const void *value_buffer, int value_len,
            adlb_data_type value_type,
            adlb_notif_t *notify,
            bool *garbage_collected);

static adlb_data_code
insert_notifications_all(adlb_datum *d, adlb_datum_id id,
          adlb_container *c, adlb_notif_t *notify, bool *garbage_collected);

static adlb_data_code
check_subscript_notifications(adlb_datum_id container_id,
    adlb_subscript subscript, struct list_l **ref_list,
    struct list_i **subscribed_ranks);

static adlb_data_code
insert_notifications2(adlb_datum *d,
      adlb_datum_id container_id, adlb_subscript subscript,
      adlb_data_type value_type, adlb_datum_storage *value,
      const void *value_buffer, int value_len,
      struct list_l *ref_list, struct list_i *sub_list,
      adlb_notif_t *notify, bool *garbage_collected);

static 
adlb_data_code append_refs(const struct list_l *subscribers,
          adlb_ref_data *references, adlb_data_type type,
          const void *value, int value_len); 

static 
adlb_data_code append_notifs(const struct list_i *listeners,
                   adlb_subscript sub, adlb_notif_ranks *notify);


static bool container_contains(adlb_container *c, adlb_subscript sub);
static bool container_lookup(adlb_container *c, adlb_subscript sub,
                             adlb_container_val *val);
static bool container_set(adlb_container *c, adlb_subscript sub,
                              adlb_container_val val,
                              adlb_container_val *prev);
static void container_add(adlb_container *c, adlb_subscript sub,
                              adlb_container_val val);

static void report_leaks(void);


// Maximum length of id/subscript string
#define ID_SUB_PAIR_MAX \
  (sizeof(adlb_datum_id) + ADLB_DATA_SUBSCRIPT_MAX + 1)

// Length of buffer for id+subscript.  Will be at most 8 bytes
// more than ADLB_SUBSCRIPT_MAX
static size_t id_sub_buflen(adlb_subscript sub)
{
  size_t size = (sizeof(adlb_datum_id) + sub.length);
  assert(size <= ID_SUB_PAIR_MAX);
  return size;
}

static size_t write_id_sub(char *buf, adlb_datum_id id,
                                  adlb_subscript sub)
{
  memcpy(buf, &id, sizeof(adlb_datum_id));
  memcpy(buf + sizeof(adlb_datum_id), sub.key, sub.length);
  return id_sub_buflen(sub);
}

// Extract id and sub from buffer.  Return internal pointer into buffer
static void read_id_sub(const char *buf, size_t buflen,
        adlb_datum_id *id, adlb_subscript *sub)
{
  assert(buflen >= sizeof(*id));
  memcpy(id, buf, sizeof(*id));
  sub->length = buflen - sizeof(*id);
  sub->key = &buf[sizeof(*id)];
}

/**
   @param s Number of servers
   @param server_num Number amongst servers
 */
adlb_data_code
xlb_data_init(int s, int server_num)
{
  assert(server_num >= 0 && server_num < s);
  servers = s;
  unique = server_num;
  if (unique == 0) unique += s;

  bool result;
  result = table_lp_init(&tds, 1024*1024);
  if (!result)
    return ADLB_DATA_ERROR_OOM;
  result = table_bp_init(&container_references, 1024*1024);
  result = table_bp_init(&container_ix_listeners, 1024*1024);
  if (!result)
    return ADLB_DATA_ERROR_OOM;

  result = table_lp_init(&locked, 16);
  if (!result)
    return ADLB_DATA_ERROR_OOM;

  last_id = LONG_MAX - servers - 1;

  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_data_create(adlb_datum_id id, adlb_data_type type,
            const adlb_type_extra *type_extra,
            const adlb_create_props *props)
{
  TRACE("data_create(%"PRId64")", id);
  check_verbose(id != ADLB_DATA_ID_NULL, ADLB_DATA_ERROR_INVALID,
                "ERROR: attempt to create data: id=%"PRId64"\n", id);

  DEBUG("Create <%"PRId64"> t:%s r:%i w:%i", id, ADLB_Data_type_tostring(type),
                                props->read_refcount, props->write_refcount);
  if (type == ADLB_DATA_TYPE_CONTAINER)
    DEBUG("Create container <%"PRId64"> k:%s v:%s", id,
          ADLB_Data_type_tostring(type_extra->CONTAINER.key_type),
          ADLB_Data_type_tostring(type_extra->CONTAINER.val_type));

#ifndef NDEBUG
  check_verbose(!table_lp_contains(&tds, id), ADLB_DATA_ERROR_DOUBLE_DECLARE,
                "<%"PRId64"> already exists", id);
#endif

  if (props->read_refcount <= 0 && props->write_refcount <= 0)
  {
    DEBUG("Skipped creation of <%"PRId64">", id);
    return ADLB_DATA_SUCCESS;
  }

  adlb_datum* d = malloc(sizeof(adlb_datum));
  check_verbose(d != NULL, ADLB_DATA_ERROR_OOM,
                "Out of memory while allocating datum");
  d->type = type;
  d->symbol = props->symbol;
  list_i_init(&d->listeners);

  table_lp_add(&tds, id, d);

  adlb_data_code dc = datum_init_props(id, d, props);
  DATA_CHECK(dc);

  // Containers need additional information
  if (type == ADLB_DATA_TYPE_CONTAINER)
  {
    dc = datum_init_container(d, type_extra->CONTAINER.key_type,
                                type_extra->CONTAINER.val_type);
    DATA_CHECK(dc);
  }
  else if (type == ADLB_DATA_TYPE_MULTISET)
  {
    datum_init_multiset(d, type_extra->MULTISET.val_type);
    DATA_CHECK(dc);
  }
  return ADLB_DATA_SUCCESS;
}

/**
   container-type data should have the subscript type set at creation
   time
 */
static adlb_data_code
datum_init_container(adlb_datum *d, adlb_data_type key_type,
                      adlb_data_type val_type)
{
  d->data.CONTAINER.members = table_bp_create(CONTAINER_INIT_CAPACITY);
  d->data.CONTAINER.key_type = key_type;
  d->data.CONTAINER.val_type = val_type;

  // Container structure is filled in, so set
  d->status.set = true;
  return ADLB_DATA_SUCCESS;
}

static adlb_data_code
datum_init_multiset(adlb_datum *d, adlb_data_type val_type)
{
  d->data.MULTISET = xlb_multiset_alloc(val_type);
  check_verbose(d->data.MULTISET != NULL, ADLB_DATA_ERROR_OOM,
                "Could not allocate multiset: out of memory");

  // Multiset structure is filled in, so mark as set
  d->status.set = true;
  return ADLB_DATA_SUCCESS;
}

/*
  Initialize datum with props.  This will garbage collect datum
  if initialized with 0 refcounts so should be called after
  the datum is otherwise set up
 */
static adlb_data_code
datum_init_props(adlb_datum_id id, adlb_datum *d,
                 const adlb_create_props *props) {
  check_verbose(props->read_refcount >= 0, ADLB_DATA_ERROR_INVALID,
                "read_refcount negative: %i", props->read_refcount);
  check_verbose(props->write_refcount >= 0, ADLB_DATA_ERROR_INVALID,
                "write_refcount negative: %i", props->write_refcount);
  d->read_refcount = props->read_refcount;
  d->write_refcount = props->write_refcount;
  xlb_data_init_status(&d->status); // default status
  d->status.permanent = props->permanent;

  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_data_exists(adlb_datum_id id, adlb_subscript subscript, bool* result)
{
  adlb_datum* d;
  table_lp_search(&tds, id, (void**)&d);

  // if subscript provided, check that subscript exists
  if (!adlb_has_sub(subscript))
  {
      if (d == NULL || !d->status.set)
        *result = false;
      else
        *result = true;
      DEBUG("Exists: <%"PRId64"> => %s", id, bool2string(*result));
  }
  else
  {
    check_verbose(d != NULL, ADLB_DATA_ERROR_INVALID,
        "<%"PRId64"> does not exist, can't check existence of subscript",
        id);
    check_verbose(d->type == ADLB_DATA_TYPE_CONTAINER, ADLB_DATA_ERROR_TYPE,
                "Expected <%"PRId64"> to be container, but had type %i",
                id, d->type);
    adlb_container_val t;
    bool data_found = container_lookup(&d->data.CONTAINER, subscript, &t);
    *result = data_found;
    // TODO: support binary keys
    DEBUG("Exists: <%"PRId64">[%.*s] => %s", id, (int)subscript.length,
            (const char*)subscript.key, bool2string(*result));
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_data_typeof(adlb_datum_id id, adlb_data_type* type)
{
  check_verbose(id != ADLB_DATA_ID_NULL, ADLB_DATA_ERROR_NULL,
                "given ADLB_DATA_ID_NULL");

  adlb_datum* d;
  bool found = table_lp_search(&tds, id, (void**)&d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  assert(d != NULL);

  *type = d->type;
  DEBUG("typeof: <%"PRId64"> => %i", id, *type);
  return ADLB_DATA_SUCCESS;
}

/**
   @param type output: the type of the subscript
               for the given container id
 */
adlb_data_code
xlb_data_container_typeof(adlb_datum_id id, adlb_data_type* key_type,
                                        adlb_data_type* val_type)
{
  adlb_datum* d;
  bool found = table_lp_search(&tds, id, (void**)&d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  assert(d != NULL);

  adlb_data_type t = d->type;
  check_verbose(t == ADLB_DATA_TYPE_CONTAINER, ADLB_DATA_ERROR_TYPE,
                "not a container: <%"PRId64">", id);
  *key_type = d->data.CONTAINER.key_type;
  *val_type = d->data.CONTAINER.val_type;
  DEBUG("container_type: <%"PRId64"> => (%i, %i)", id, *key_type, *val_type);
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_data_permanent(adlb_datum_id id) {
  adlb_datum* d;
  bool found = table_lp_search(&tds, id, (void**)&d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  assert(d != NULL);
  d->status. permanent = true;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_datum_lookup(adlb_datum_id id, adlb_datum **d)
{
  bool found = table_lp_search(&tds, id, (void**)d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  assert(*d != NULL);
  return ADLB_DATA_SUCCESS;
}

/**
   @param garbaged_collected: whether the data was freed
                              (if null, not modified);
   Allocates fresh memory in notify_ranks unless notify_count==0
   Caller must free result
 */
adlb_data_code
xlb_data_reference_count(adlb_datum_id id, adlb_refcounts change,
          refcount_scavenge scav, bool *garbage_collected,
          adlb_refcounts *refcounts_scavenged,
          adlb_notif_ranks *notifications)
{
  adlb_datum* d;
  adlb_data_code dc = xlb_datum_lookup(id, &d);
  DATA_CHECK(dc);
  return xlb_rc_impl(d, id, change, scav, garbage_collected,
                       refcounts_scavenged, notifications);
}

adlb_data_code
xlb_rc_impl(adlb_datum *d, adlb_datum_id id,
          adlb_refcounts change, refcount_scavenge scav,
          bool *garbage_collected, adlb_refcounts *refcounts_scavenged,
          adlb_notif_ranks *notifications)
{
  // default: didn't garbage collect
  if (garbage_collected != NULL)
    *garbage_collected = false;
  if (refcounts_scavenged != NULL)
    *refcounts_scavenged = ADLB_NO_RC;

  assert(scav.refcounts.read_refcount >= 0);
  assert(scav.refcounts.write_refcount >= 0);

  int read_incr = change.read_refcount;
  int write_incr = change.write_refcount;

  bool do_gc = d->read_refcount + read_incr <= 0 &&
               d->write_refcount + write_incr <= 0;
  
  if (!ADLB_RC_IS_NULL(scav.refcounts))
  {
    // Don't go through with decrement if caller wants to scavenge refcounts
    // and we can't get at least one.
    // This is because, otherwise, there is a race condition where this
    // item may be garbage-collected before the referands have their
    // counts incremented.
    if (!do_gc)
      return ADLB_DATA_SUCCESS;

    // Will only hold one refcount on referand per reference in datum
    if (refcounts_scavenged != NULL)
    {
      if (scav.refcounts.read_refcount > 0)
        refcounts_scavenged->read_refcount = 1;
      if (scav.refcounts.write_refcount > 0)
        refcounts_scavenged->write_refcount = 1;
    }
  }

  if (read_incr != 0) {
    // Shouldn't get here if disabled
    check_verbose(xlb_read_refcount_enabled, ADLB_DATA_ERROR_INVALID,
                  "Internal error: should not get here with read reference "
                  "counting disabled");

    if (d->status.permanent) {
        // Ignore read reference count operations for permanent variables
        return ADLB_DATA_SUCCESS;
      }
    // Should not go negative
    check_verbose(d->read_refcount > 0 &&
                   d->read_refcount + read_incr >= 0,
                ADLB_DATA_ERROR_SLOTS_NEGATIVE,
                "<%"PRId64"> read_refcount: %i incr: %i",
                id, d->read_refcount, read_incr);
    d->read_refcount += read_incr;
    DEBUG("read_refcount: <%"PRId64"> => %i", id, d->read_refcount);
  }

  if (write_incr != 0) {
    // Should not go negative
    check_verbose(d->write_refcount > 0 &&
                   d->write_refcount + write_incr >= 0,
                ADLB_DATA_ERROR_SLOTS_NEGATIVE,
                "<%"PRId64"> write_refcount: %i incr: %i",
                id, d->write_refcount, write_incr);
    d->write_refcount += write_incr;
    if (d->write_refcount == 0) {
      adlb_data_code dc;
      dc = xlb_data_close(id, d, notifications);
      DATA_CHECK(dc);
    }
    DEBUG("write_refcount: <%"PRId64"> => %i", id, d->write_refcount);
  }

  if (d->read_refcount <= 0 && d->write_refcount <= 0) {
    if (garbage_collected != NULL)
      *garbage_collected = true;
    return datum_gc(id, d, scav);
  }

  return ADLB_DATA_SUCCESS;
}

static adlb_data_code
extract_members(adlb_container *c, int count, int offset,
                bool include_keys, bool include_vals,
                const adlb_buffer *caller_buffer,
                adlb_buffer *output);

static adlb_data_code
datum_gc(adlb_datum_id id, adlb_datum* d,
           refcount_scavenge scav)
{
  DEBUG("datum_gc: <%"PRId64">", id);
  check_verbose(!d->status.permanent, ADLB_DATA_ERROR_UNKNOWN,
          "Garbage collecting permanent data");

  if (d->status.set)
  {
    // Cleanup the storage if initialized
    adlb_data_code dc = xlb_datum_cleanup(&d->data, d->type, id, scav);
    DATA_CHECK(dc);
  }

  // This list should be empty since data being destroyed
  check_verbose(d->listeners.size == 0, ADLB_DATA_ERROR_TYPE,
                "%i listeners for garbage collected td <%"PRId64">",
                d->listeners.size, id);

  void *tmp;
  table_lp_remove(&tds, id, &tmp);
  assert(tmp == d);

  free(d);
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_data_referand_refcount(const void *data, int length,
        adlb_data_type type, adlb_datum_id id,
        adlb_refcounts change)
{
  adlb_data_code dc, rc;
  adlb_datum_storage d;
  dc = ADLB_Unpack(&d, type, data, length);
  DATA_CHECK(dc);

  rc = xlb_incr_referand(&d, type, change);
  dc = ADLB_Free_storage(&d, type);
  DATA_CHECK(dc);
  return rc;
}


adlb_data_code
xlb_data_lock(adlb_datum_id id, int rank, bool* result)
{
  adlb_datum* d;
  bool found = table_lp_search(&tds, id, (void**)&d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  assert(d != NULL);

  if (table_lp_contains(&locked, id))
  {
    *result = false;
    return ADLB_DATA_SUCCESS;
  }
  else
  {
    int* r = malloc(sizeof(int));
    *r = rank;
    *result = true;
    table_lp_add(&locked, id, (void*)r);
  }

  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_data_unlock(adlb_datum_id id)
{
  int* r;
  bool found = table_lp_remove(&locked, id, (void**)&r);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  free(r);
  return ADLB_DATA_SUCCESS;
}

/**
   @param if not null and data type is container, subscribe
          to this subscript
   @param result set to 1 iff subscribed, else 0 (td closed)
   @return ADLB_SUCCESS or ADLB_ERROR
 */
adlb_data_code
xlb_data_subscribe(adlb_datum_id id, adlb_subscript subscript,
              int rank, int* result)
{
  if (!adlb_has_sub(subscript))
  {
    DEBUG("data_subscribe(): <%"PRId64">", id);
  }
  else
  {
    // TODO: support binary keys
    DEBUG("data_subscribe(): <%"PRId64">[%.*s]", id, (int)subscript.length,
            (const char*)subscript.key);
  }

  adlb_datum* d;
  bool found = table_lp_search(&tds, id, (void**)&d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  assert(d != NULL);

  bool subscribed;

  if (adlb_has_sub(subscript))
  {
    // TODO: support binary keys
    check_verbose(d->type == ADLB_DATA_TYPE_CONTAINER,
            ADLB_DATA_ERROR_INVALID, "subscribing to subscript %.*s on "
            "non-container: <%"PRId64">", (int)subscript.length,
            (const char*)subscript.key, id);
    
    if (container_contains(&d->data.CONTAINER, subscript))
    {
      subscribed = false;
    }
    else
    {
      // encode container, index and ref type into string
      char key[id_sub_buflen(subscript)];
      size_t key_len = write_id_sub(key, id, subscript);

      struct list_i* listeners = NULL;
      found = table_bp_search(&container_ix_listeners, key, key_len,
                                (void*)&listeners);
      if (!found)
      {
        // Nobody else has subscribed to this pair yet
        listeners = list_i_create();
        table_bp_add(&container_ix_listeners, key, key_len, listeners);
      }
      TRACE("Added %i to listeners for %"PRId64"[%.*s]\n", rank,
          id, (int)subscript.length, (const char*)subscript.key);
      list_i_unique_insert(listeners, rank);
      subscribed = true;
    }
  }
  else
  {
    // No subscript, so subscribing to top-level datum
    if (d->write_refcount == 0)
    {
      subscribed = false;
    }
    else
    {
      list_i_unique_insert(&d->listeners, rank);
      subscribed = true;
    }
  }

  *result = subscribed ? 1 : 0;
  return ADLB_DATA_SUCCESS;
}

/*
    data_container_reference consumes a read reference count unless
    it immediately returns a result.  If it returns a result,
    the caller is responsible for setting references and then
    decrementing the read reference count of the container.
 */
adlb_data_code xlb_data_container_reference(adlb_datum_id container_id,
                                        adlb_subscript subscript,
                                        adlb_datum_id reference,
                                        adlb_data_type ref_type,
                                        const adlb_buffer *caller_buffer,
                                        adlb_binary_data *result)
{
  // Check that container_id is an initialized container
  adlb_datum* d;
  bool found = table_lp_search(&tds, container_id, (void**)&d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", container_id);
  assert(d != NULL);

  if (ref_type != d->data.CONTAINER.val_type)
  {
    printf("Type mismatch when setting up reference expected %i actual %i\n",
            ref_type, d->data.CONTAINER.val_type);
    return ADLB_DATA_ERROR_TYPE;
  }

  // Is the subscript already pointing to a data identifier?
  adlb_container_val t;

  bool data_found = container_lookup(&d->data.CONTAINER, subscript, &t);
  TRACE("lookup container for ref: %"PRId64"[%.*s]: %i", container_id,
          (int)subscript.length, subscript.key, (int)data_found);
  if (data_found && t != NULL)
  {
    adlb_data_code dc = ADLB_Pack(t, d->data.CONTAINER.val_type,
                                      caller_buffer, result);
    DATA_CHECK(dc);

    return ADLB_DATA_SUCCESS;
  }

  result->data = result->caller_data = NULL; // Signal data not found

  // Is the container closed?
  // TODO: support binary keys
  check_verbose(d->write_refcount > 0, ADLB_DATA_ERROR_INVALID,
        "Attempting to subscribe to non-existent subscript\n"
        "on a closed container:  <%"PRId64">[%.*s]\n",
        container_id, (int)subscript.length, (const char*)subscript.key);
  check_verbose(d->read_refcount > 0, ADLB_DATA_ERROR_INVALID,
        "Container_reference consumes a read reference count, but "
        "reference count was %d for <%"PRId64">", d->read_refcount,
        container_id);


  // encode container, index and ref type into string
  char key[id_sub_buflen(subscript)];
  size_t key_len = write_id_sub(key, container_id, subscript);

  struct list_l* listeners = NULL;
  found = table_bp_search(&container_references, key, key_len,
                            (void*)&listeners);
  TRACE("search container_ref %"PRId64"[%.*s]: %i", container_id,
          (int)subscript.length, subscript.key, (int)found);
  if (!found)
  {
    // Nobody else has subscribed to this pair yet
    listeners = list_l_create();
    TRACE("add container_ref %"PRId64"[%.*s]", container_id,
          (int)subscript.length, subscript.key);
    table_bp_add(&container_references, key, key_len, listeners);
  }
  else
  {
    // Only have one read refcount per subscribed index
    // There should be at least 2 read refcounts: one for
    //  this call to container_reference, and one for the
    //  subscriber list
    if (xlb_read_refcount_enabled) {
      assert(d->read_refcount >= 2);
      d->read_refcount--;
      
      DEBUG("read_refcount in container_reference: <%"PRId64"> => %i",
          container_id, d->read_refcount);
    }
  }

  // TODO: support binary keys
  check_verbose(listeners != NULL, ADLB_DATA_ERROR_NULL,
                "Found null value in listeners table\n"
                "for:  %"PRId64"[%.*s]\n", container_id,
                (int)subscript.length, (const char*)subscript.key);

  TRACE("Added %"PRId64" to listeners for %"PRId64"[%s]", reference,
        container_id, subscript);
  list_l_unique_insert(listeners, reference);
  result->data = NULL;
  return ADLB_DATA_SUCCESS;
}

/**
   Can allocate fresh memory in notifications
   Caller must free result
   type: type of data to be assigned
 */
adlb_data_code
xlb_data_store(adlb_datum_id id, adlb_subscript subscript,
          const void* buffer, int length,
          adlb_data_type type,
          adlb_refcounts refcount_decr,
          adlb_notif_t *notifications)
{
  assert(length >= 0);
  
  // Initialize notifications to empty to be sure
  notifications->notify.count = 0;
  notifications->references.count = 0;
  notifications->to_free = NULL;
  notifications->to_free_length = 0;

  adlb_datum* d;
  bool found = table_lp_search(&tds, id, (void**)&d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  assert(d != NULL);

  // Make sure we are allowed to write this data
  if (d->write_refcount <= 0)
  {
    // Don't print error by default: caller may want to handle
    DEBUG("attempt to write closed var: <%"PRId64">", id);
    return ADLB_DATA_ERROR_DOUBLE_WRITE;
  }

  // Track if we freed datum for error detection
  bool freed_datum = false;

  adlb_data_code dc;
  if (!adlb_has_sub(subscript))
  {
    check_verbose(type == d->type, ADLB_DATA_ERROR_TYPE,
            "Type mismatch: expected %s actual %s\n",
            ADLB_Data_type_tostring(type), ADLB_Data_type_tostring(d->type));

    // Handle store to top-level datum
    dc = ADLB_Unpack2(&d->data, d->type, buffer, length, false);
    DATA_CHECK(dc);
    d->status.set = true;

    if (ENABLE_LOG_DEBUG && xlb_debug_enabled)
    {
      char *val_s = ADLB_Data_repr(&d->data, d->type);
      DEBUG("data_store <%"PRId64">=%s\n", id, val_s);
      free(val_s);
    }
    
    // If this was a container, need to handle reference notifications
    // TODO: need to pass back subscripts and data for references
    if (type == ADLB_DATA_TYPE_CONTAINER)
    {
      // TODO: deserialize container value
      // TODO; append to to_free list
      dc = insert_notifications_all(d, id, &d->data.CONTAINER, 
                notifications, &freed_datum);
      DATA_CHECK(dc);
    }
  }
  else if (d->type == ADLB_DATA_TYPE_MULTISET)
  {
    // Any subscript appends to multiset
    assert(adlb_has_sub(subscript));
    check_verbose(adlb_has_sub(subscript), ADLB_DATA_ERROR_TYPE,
                  "Cannot provide subscript when appending to multiset");
    adlb_data_type elem_type = d->data.MULTISET->elem_type;
    check_verbose(type == elem_type, ADLB_DATA_ERROR_TYPE,
            "Type mismatch for multiset val: expected %s actual %s\n",
            ADLB_Data_type_tostring(elem_type), ADLB_Data_type_tostring(type));
    // Handle addition to multiset
    const adlb_datum_storage *elem;
    dc = xlb_multiset_add(d->data.MULTISET, buffer, length, &elem);
    DATA_CHECK(dc);

    if (ENABLE_LOG_DEBUG && xlb_debug_enabled)
    {
      char *val_s = ADLB_Data_repr(elem, elem_type);
      DEBUG("data_store <%"PRId64">+=%s\n", id, val_s);
      free(val_s);
    }
  }
  else
  {
    // Handle insert
    check_verbose(d->type == ADLB_DATA_TYPE_CONTAINER, ADLB_DATA_ERROR_TYPE,
                  "insert to type %s not supported: <%"PRId64">",
                  ADLB_Data_type_tostring(d->type), id);

    adlb_container *c = &d->data.CONTAINER;

    check_verbose(type == c->val_type, ADLB_DATA_ERROR_TYPE,
                  "Type mismatch for container value: "
                  "given: %s required: %s\n",
                  ADLB_Data_type_tostring(type),
                  ADLB_Data_type_tostring(c->val_type));

    // Does the link already exist?
    adlb_container_val t = NULL;
    found = container_lookup(c, subscript, &t);

    if (found && t != NULL)
    {
      // If present, must be an UNLINKED entry:
      // TODO: support binary keys
      // Don't print error by default: caller may want to handle
      DEBUG("already exists: <%"PRId64">[%.*s]", id, (int)subscript.length,
            (const char*)subscript.key);
      return ADLB_DATA_ERROR_DOUBLE_WRITE;
   } 

    
    // Now we are guaranteed to succeed
    adlb_datum_storage *entry = malloc(sizeof(adlb_datum_storage));
    dc = ADLB_Unpack(entry, c->val_type, buffer, length);
    DATA_CHECK(dc);

    if (found)
    {
      DEBUG("Assigning unlinked precreated entry");
      // Ok- somebody did an Insert_atomic
      adlb_container_val v;
      // Reset entry
      bool b = container_set(c, subscript, entry, &v);
      ASSERT(b);
      ASSERT(v == NULL); // Should have been NULL for unlinked
    }
    else
    {
      DEBUG("Creating new container entry");
      container_add(c, subscript, entry);
    }

    dc = insert_notifications(d, id, subscript, entry, 
              buffer, length, c->val_type,
              notifications, &freed_datum);
    DATA_CHECK(dc);


    if (ENABLE_LOG_DEBUG && xlb_debug_enabled)
    {
      char *val_s = ADLB_Data_repr(entry, c->val_type);
      // TODO: support binary keys
      DEBUG("data_store <%"PRId64">[%.*s]=%s\n", id, (int)subscript.length,
            (const char*)subscript.key, val_s);
      free(val_s);
    }
  }

  // Handle reference count decrease
  assert(refcount_decr.write_refcount >= 0);
  assert(refcount_decr.read_refcount >= 0);
  if (refcount_decr.write_refcount > 0 || refcount_decr.read_refcount > 0)
  {
    // Avoid accessing freed memory
    check_verbose(!freed_datum, ADLB_DATA_ERROR_SLOTS_NEGATIVE,
        "Taking write reference count below zero on datum <%"PRId64">", id);

    adlb_refcounts incr = { .read_refcount = xlb_read_refcount_enabled ?
                                            -refcount_decr.read_refcount : 0,
                            .write_refcount = -refcount_decr.write_refcount };
    dc = xlb_rc_impl(d, id, incr, NO_SCAVENGE,
                     NULL, NULL, &notifications->notify);
    DATA_CHECK(dc);
  }

  return ADLB_DATA_SUCCESS;
}

/**
   Notify all waiters on variable that it was closed

   Allocates fresh memory in result unless count==0
   Caller must free result
 */
static adlb_data_code
xlb_data_close(adlb_datum_id id, adlb_datum *d, adlb_notif_ranks *notify)
{
  assert(d != NULL);
  adlb_subscript no_subscript = ADLB_NO_SUB;
  DEBUG("data_close: <%"PRId64"> listeners: %i", id, d->listeners.size);
  adlb_data_code dc = append_notifs(&d->listeners, no_subscript, notify);
  DATA_CHECK(dc);
  list_i_clear(&d->listeners);

  TRACE_END;
  return ADLB_DATA_SUCCESS;
}

/**
   Used by data_retrieve()
*/
#define CHECK_SET(id, d)                              \
  if (!d->status.set) {                               \
    printf("not set: %"PRId64"\n", id);                    \
    return ADLB_DATA_ERROR_UNSET;                     \
  }

/**
   Retrieve works on UNSET data for files and containers:
                  this is necessary for filenames,
                  and may be useful for containers.
   Caller should use result before making further calls into
   this module, except if type is container, in which case the
   caller must free result pointer.  This is because the container
   subscript list must be dynamically created.
   @param subscript optional subscript.  If provided, looks up container element
   @param type   Returns type
   @param result Returns pointer to data.  This data may be internal data in
                    data module, or may be freshly allocated memory that caller
                    must free
   @param malloced_result if data_retrieve allocated memory, this stores a
                    pointer to the allocated memory.
   @param length Returns length of string
   @returns ADLB_DATA_ERROR_NOT_FOUND if id not found
            ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND if id found, but not subscript
 */
adlb_data_code
xlb_data_retrieve(adlb_datum_id id, adlb_subscript subscript,
              adlb_data_type* type,
              const adlb_buffer *caller_buffer,
              adlb_binary_data *result)
{
  TRACE("data_retrieve(%"PRId64", %s)", id, subscript);

  adlb_data_code dc;

  result->data = result->caller_data = NULL;

  adlb_datum* d;
  bool found = table_lp_search(&tds, id, (void**)&d);
  if (!found)
  {
    TRACE("data_retrieve(%"PRId64"): NOT FOUND", id);
    return ADLB_DATA_ERROR_NOT_FOUND;
  }
  assert(d != NULL);

  if (!adlb_has_sub(subscript))
  {
    *type = d->type;
    CHECK_SET(id, d);
    return ADLB_Pack(&d->data, d->type, caller_buffer, result);
  }
  else
  {
    switch (d->type) {
      case ADLB_DATA_TYPE_CONTAINER:
      {
        *type = d->data.CONTAINER.val_type;

        adlb_container_val t;
        found = container_lookup(&d->data.CONTAINER, subscript, &t);
        if (!found)
        {
          DEBUG("SUBSCRIPT NOT FOUND");
          return ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND;
        }
        else if (t == NULL)
        {
          DEBUG("SUBSCRIPT CREATED BUT NOT LINKED");
          return ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND;
        }

        return ADLB_Pack(t, d->data.CONTAINER.val_type, caller_buffer, result);
      }
      case ADLB_DATA_TYPE_STRUCT:
      {
        int field_ix;
        dc = xlb_struct_str_to_ix(subscript, &field_ix);
        DATA_CHECK(dc);

        const adlb_datum_storage *v;
        dc = xlb_struct_get_field(d->data.STRUCT, field_ix, &v, type);
        DATA_CHECK(dc);
        return ADLB_Pack(v, *type, caller_buffer, result);
      }
      default:
        verbose_error(ADLB_DATA_ERROR_INVALID, "Cannot lookup subscript "
                        "on type: %s", ADLB_Data_type_tostring(d->type));
    }
  }
  // Unreachable
  assert(false);
  return ADLB_DATA_ERROR_UNKNOWN;
}

/**
   Helper function to add to container
 */
static void container_add(adlb_container *c, adlb_subscript sub,
                              adlb_container_val val)
{
  TRACE("Adding %p to %p", val, c);
  table_bp_add(c->members, sub.key, sub.length, val);
}

/**
   Helper function to set existing container val
 */
static bool container_set(adlb_container *c, adlb_subscript sub,
                              adlb_container_val val,
                              adlb_container_val *prev)
{
  return table_bp_set(c->members, sub.key, sub.length, val, (void**)prev);
}

static bool container_contains(adlb_container *c, adlb_subscript sub)
{
  adlb_container_val tmp;
  return container_lookup(c, sub, &tmp);
}

/**
   Helper function for looking up container
  */
static bool container_lookup(adlb_container *c, adlb_subscript sub,
                             adlb_container_val *val)
{
  return table_bp_search(c->members, sub.key, sub.length, (void**)val);
}

static adlb_data_code
pack_member(adlb_container *cont, table_bp_entry *item,
            bool include_keys, bool include_vals,
            const adlb_buffer *tmp_buf, adlb_buffer *result,
            bool *result_caller_buffer, int *result_pos);

/**
   Extract the table members into a buffer.
   count: -1 for all past offset, or the exact expected count based
        on the array size.
 */
static adlb_data_code
extract_members(adlb_container *cont, int count, int offset,
                bool include_keys, bool include_vals,
                const adlb_buffer *caller_buffer,
                adlb_buffer *output)
{
  int c = 0; // Count of members seen
  adlb_data_code dc;
  struct table_bp* members = cont->members;
  bool use_caller_buf;

  dc = ADLB_Init_buf(caller_buffer, output, &use_caller_buf, 65536);
  ADLB_DATA_CHECK(dc);

  // Allocate some temporary storage on stack
  adlb_buffer tmp_buf;
  tmp_buf.length = 4096;
  char tmp_storage[4096];
  tmp_buf.data = tmp_storage;

  int output_pos = 0; // Amount of output used

  TABLE_BP_FOREACH(members, item)
  {
    if (c >= offset)
    {
      if (c >= count+offset && count != -1)
      {
        TRACE("Got %i/%i items, done\n", c+1, count);
        goto extract_members_done;
      }
      dc = pack_member(cont, item, include_keys, include_vals, &tmp_buf,
                       output, &use_caller_buf, &output_pos);
      DATA_CHECK(dc);
    }
    c++;
  }

  TRACE("Got %i/%i entries at offset %i table size %i\n", c-offset, count,
                offset, members->size);
  // Should have found requested number
  if (count != -1 && c - offset != count)
  {
    DEBUG("Warning: did not get expected count when enumerating array. "
          "Got %i/%i entries at offset %i table size %i\n",
          c-offset, count, offset, members->size);
  }

extract_members_done:
  // Mark actual length of output
  output->length = output_pos;
  TRACE("extract_members: output_length: %i\n", output->length);
  return ADLB_DATA_SUCCESS;
}

static adlb_data_code
pack_member(adlb_container *cont, table_bp_entry *item,
            bool include_keys, bool include_vals,
            const adlb_buffer *tmp_buf, adlb_buffer *result,
            bool *result_caller_buffer, int *result_pos)
{
  assert(table_bp_entry_valid(item));

  adlb_data_code dc;
  if (include_keys)
  {
    assert(item->key_len <= INT_MAX);
    dc = ADLB_Append_buffer(ADLB_DATA_TYPE_NULL, 
            table_bp_get_key(item), (int)item->key_len,
            true, result, result_caller_buffer, result_pos);
    DATA_CHECK(dc);
  }
  if (include_vals)
  {
    dc = ADLB_Pack_buffer(item->data, cont->val_type, true,
                tmp_buf, result, result_caller_buffer, result_pos);
    DATA_CHECK(dc);
  }

  return ADLB_DATA_SUCCESS;
}

static int
enumerate_slice_size(int offset, int count, int actual_size)
{
  // Number of elements after offset
  int post_offset = actual_size - offset;
  if (post_offset < 0)
  {
    // might be negative
    post_offset = 0;
  }
  if (count < 0) {
    // Unlimited count
    return post_offset;
  } else if (count <= post_offset) {
    // Slice size limited by specified count
    return count;
  } else {
    return post_offset;
  }
}

/**
   @param container_id
   @param count maximum number of elements to return, negative for unlimited
   @param offset offset of member to start at
   @param data Filled in with output location for encoded binary keys and
               values.  Members are stored with key first, then value.  The
               length in bytes of the key and value is encoded with vint_encode
               and prefixed to the actual data
   @param length Length of data in data
   @param include_keys whether to include keys in result
   @param include_vals whether to include values in result
   @param actual Returns the number of entries in the container
 */
adlb_data_code
xlb_data_enumerate(adlb_datum_id id, int count, int offset,
               bool include_keys, bool include_vals,
               const adlb_buffer *caller_buffer,
               adlb_buffer *data, int* actual,
               adlb_data_type *key_type, adlb_data_type *val_type)
{
  TRACE("data_enumerate(%"PRId64")", id);
  adlb_data_code dc;
  adlb_datum* d;
  bool found = table_lp_search(&tds, id, (void**)&d);

  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", id);
  assert(d != NULL);
  if (d->type == ADLB_DATA_TYPE_CONTAINER)
  {
    int slice_size = enumerate_slice_size(offset, count,
                        d->data.CONTAINER.members->size);

    if (include_keys || include_vals)
    {
      dc = extract_members(&d->data.CONTAINER, count, offset,
                                  include_keys, include_vals,
                                  caller_buffer, data);
      DATA_CHECK(dc);
    }

    *actual = slice_size;
    *key_type = d->data.CONTAINER.key_type;
    *val_type = d->data.CONTAINER.val_type;
    TRACE("Enumerate container: %i elems %i bytes\n", slice_size,
                                                      data->length);
    return ADLB_DATA_SUCCESS;
  }
  else if (d->type == ADLB_DATA_TYPE_MULTISET)
  {
    check_verbose(!include_keys, ADLB_DATA_ERROR_TYPE, "<%"PRId64"> "
        " with type multiset does not have keys to enumerate", id);
    int slice_size = enumerate_slice_size(offset, count,
                              (int)xlb_multiset_size(d->data.MULTISET));

    if (include_vals) {
      // Extract members to buffer
      dc = xlb_multiset_extract_slice(d->data.MULTISET, offset, slice_size,
                                      caller_buffer, data);
      DATA_CHECK(dc);
    }

    *actual = slice_size;
    *key_type = ADLB_DATA_TYPE_NULL;
    *val_type = d->data.MULTISET->elem_type;
    TRACE("Enumerate multiset: %i elems %i bytes\n", slice_size,
                                                     data->length);
    return ADLB_DATA_SUCCESS;
  }
  else
  {
    verbose_error(ADLB_DATA_ERROR_TYPE, "enumeration of <%"PRId64"> with "
            "type %s not supported", id, ADLB_Data_type_tostring(d->type));
  }
  // Unreachable
  return ADLB_DATA_ERROR_UNKNOWN;
}

adlb_data_code
xlb_data_container_size(adlb_datum_id container_id, int* size)
{
  adlb_datum* c;
  bool found = table_lp_search(&tds, container_id, (void**)&c);

  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%"PRId64">", container_id);
  assert(c != NULL);

  switch (c->type)
  {
    case ADLB_DATA_TYPE_CONTAINER:
      *size = c->data.CONTAINER.members->size;
      return ADLB_DATA_SUCCESS;
    case ADLB_DATA_TYPE_MULTISET:
      *size = (int)xlb_multiset_size(c->data.MULTISET);
      return ADLB_DATA_SUCCESS;
    default:
      printf("not a container or multiset: <%"PRId64">", container_id);
      return ADLB_DATA_ERROR_TYPE;
  }
}

static adlb_data_code
insert_notifications(adlb_datum *d,
            adlb_datum_id container_id,
            adlb_subscript subscript,
            adlb_datum_storage *inserted_value,
            const void *value_buffer, int value_len,
            adlb_data_type value_type,
            adlb_notif_t *notify,
            bool *garbage_collected)
{
  adlb_data_code dc;

  struct list_l *ref_list = NULL;
  struct list_i *sub_list = NULL;

  // Find, remove, and return any listeners/references
  dc = check_subscript_notifications(container_id, subscript, &ref_list,
                                     &sub_list);
  DATA_CHECK(dc);
  
  dc = insert_notifications2(d, container_id, subscript,
      value_type, inserted_value, value_buffer, value_len,
      ref_list, sub_list, notify, garbage_collected);
  DATA_CHECK(dc);

  // Track whether we garbage collected the data
  assert(garbage_collected != NULL);
  *garbage_collected = false;

  TRACE("remove container_ref %"PRId64"[%.*s]: %i\n", container_id,
        (int)subscript.length, subscript.key, (int)result);
  return ADLB_DATA_SUCCESS;
}

/*
  Check for subscribers for an id/subscript pair and set output arguments
  if found.
 */
static adlb_data_code
check_subscript_notifications(adlb_datum_id container_id,
    adlb_subscript subscript, struct list_l **ref_list,
    struct list_i **sub_list) {
  char s[id_sub_buflen(subscript)];
  size_t s_len = write_id_sub(s, container_id, subscript);
  void *data;
  bool result = table_bp_remove(&container_references, s, s_len, &data);

  if (result)
  {
    *ref_list = (struct list_l*) data;
  }
  
  result = table_bp_remove(&container_ix_listeners, s, s_len, &data);

  if (result)
  {
    *sub_list = (struct list_i*) data;
  }

  return ADLB_DATA_SUCCESS;
}

/*
  Process the notifications once we've extracted lists
  value_buffer/value_len: data must be provided if ref_list is set.
        This memory should have a lifetime matching that of the
        whole notification data structure (i.e. we don't make a
        copy of it and store a pointer in there)
 */
static adlb_data_code
insert_notifications2(adlb_datum *d,
      adlb_datum_id container_id, adlb_subscript subscript,
      adlb_data_type value_type, adlb_datum_storage *value,
      const void *value_buffer, int value_len,
      struct list_l *ref_list, struct list_i *sub_list,
      adlb_notif_t *notify, bool *garbage_collected)
{
  adlb_data_code dc;
  if (ref_list != NULL)
  {
    int nreferences = ref_list->size;
    dc = append_refs(ref_list, &notify->references, value_type,
                     value_buffer, value_len);
    DATA_CHECK(dc);
    list_l_free(ref_list);

    if (xlb_read_refcount_enabled)
    {
      // TODO: use scavenging here
      // TODO: offload this to client?
      // TODO: does initial call need to tell use whether it was a read or
      //       write reference?
      // the referenced variables need refcount incremented, since we're
      // going to create a new reference to them
      adlb_refcounts referand_incr = { .read_refcount = nreferences,
                                       .write_refcount = 0 };
      dc = xlb_incr_referand(value, value_type, referand_incr);
      DATA_CHECK(dc);

      // Now that references are incremented on ref variables,
      // no longer need read reference for waiters on this index
      adlb_notif_ranks tmp = ADLB_NO_NOTIF_RANKS;
      adlb_refcounts read_decr = { .read_refcount = -1,
                                   .write_refcount = 0 };
      dc = xlb_rc_impl(d, container_id, read_decr, NO_SCAVENGE,
                         garbage_collected, NULL, &tmp);
      DATA_CHECK(dc);
      assert(tmp.count == 0);
    }
  }

  
  if (sub_list != NULL && sub_list->size > 0)
  {
    dc = append_notifs(sub_list, subscript, &notify->notify);
    DATA_CHECK(dc);
    list_i_free(sub_list);
  }
  return ADLB_DATA_SUCCESS;
  return ADLB_DATA_SUCCESS;
}

/*
  Check for references to all members.
 */
static adlb_data_code
insert_notifications_all(adlb_datum *d, adlb_datum_id id,
          adlb_container *c, adlb_notif_t *notify, bool *garbage_collected)
{
  adlb_data_code dc;
  struct table_bp* members = c->members;
  TABLE_BP_FOREACH(members, item)
  {
    adlb_subscript sub = { .key = table_bp_get_key(item),
                           .length = item->key_len };

    // Find, remove, and return any listeners/references
    struct list_l *ref_list = NULL;
    struct list_i *sub_list = NULL;
    dc = check_subscript_notifications(id, sub, &ref_list, &sub_list);
    DATA_CHECK(dc);

    if (ref_list != NULL || sub_list != NULL)
    {
      adlb_container_val val = item->data;
      adlb_binary_data val_data;
      if (ref_list != NULL)
      {
        // Pack container value to binary value if needed
        dc = ADLB_Pack(val, c->val_type, NULL, &val_data);
        DATA_CHECK(dc);

        // Take ownership of data in case it is freed
        dc = ADLB_Own_data(NULL, &val_data);
        DATA_CHECK(dc);

        // Mark that caller should free
        if (notify->to_free_length == notify->to_free_size)
        {
          notify->to_free_size = notify->to_free_size == 0 ? 
                  64 : notify->to_free_size * 2;
          DATA_REALLOC(notify->to_free, notify->to_free_size);
        }
        notify->to_free[notify->to_free_length++] = val_data.caller_data;
      }
      dc = insert_notifications2(d, id, sub, c->val_type, item->data,
                    val_data.data, val_data.length, ref_list, sub_list,
                    notify, garbage_collected);
      DATA_CHECK(dc);

      if (*garbage_collected)
      {
        // We just processed the last pending notification for the
        // container: we're done!
        return ADLB_DATA_SUCCESS;
      }
    }
  }
  return ADLB_DATA_SUCCESS;
}

static 
adlb_data_code append_refs(const struct list_l *subscribers,
          adlb_ref_data *references, adlb_data_type type,
          const void *value, int value_len)
{
  int nrefs = subscribers->size;
  if (nrefs > 0)
  {
    // append reference data
    DATA_REALLOC(references->data, (size_t)(nrefs + references->count));

    struct list_l_item *node = subscribers->head;
    for (int i = 0; i < nrefs; i++)
    {
      assert(node != NULL); // Shouldn't fail if size was ok
      adlb_ref_datum *ref = &references->data[i + references->count];
      ref->id = node->data;
      ref->type = type;
      ref->value = value;
      ref->value_len = value_len;
      node = node->next;
    }
    references->count += nrefs;
  }

  return ADLB_DATA_SUCCESS;
}

static 
adlb_data_code append_notifs(const struct list_i *listeners,
                   adlb_subscript sub, adlb_notif_ranks *notify)
{
  assert(notify->count >= 0);
  int nlisteners = listeners->size;
  assert(nlisteners >= 0);
  if (nlisteners == 0)
    return ADLB_DATA_SUCCESS;

  DATA_REALLOC(notify->notifs, (size_t)(notify->count + nlisteners));

  struct list_i_item *node = listeners->head;
  for (int i = 0; i < nlisteners; i++)
  {
    assert(node != NULL); // If null, list size was wrong
    adlb_notif_rank *nrank = &notify->notifs[i + notify->count];
    nrank->rank = node->data;
    nrank->subscript = sub;
    node = node->next;
  }
  notify->count += nlisteners;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_data_insert_atomic(adlb_datum_id container_id, adlb_subscript subscript,
                   bool* created, bool *value_present)
{
  adlb_datum* d;
  bool found = table_lp_search(&tds, container_id, (void**)&d);
  check_verbose(found, ADLB_DATA_ERROR_NOT_FOUND,
                "container not found: <%"PRId64">", container_id);
  assert(d != NULL);
  check_verbose(d->type == ADLB_DATA_TYPE_CONTAINER,
                ADLB_DATA_ERROR_TYPE,
                "not a container: <%"PRId64">", container_id);

  // Does the link already exist?
  adlb_container_val val;
  bool key_exists = container_lookup(&d->data.CONTAINER, subscript, &val);
  if (key_exists)
  {
    *created = false;
    *value_present = (val != NULL);
    return ADLB_DATA_SUCCESS;
  }

  // Use NULL pointer value to represent unlinked
  container_add(&d->data.CONTAINER, subscript, NULL);
  *created = true;
  return ADLB_DATA_SUCCESS;
}


/**
   Obtain an unused TD
   @return Successful unless we have exhausted the
           set of signed long integers,
           in which case return ADLB_DATA_ID_NULL
 */
adlb_data_code
xlb_data_unique(adlb_datum_id* result)
{
  // Tim: can we remove this loop and save table lookup?
  // I don't think in general that we can reasonably support
  // user code mixing up its own IDs and ADLB-assigned IDs
  /*
  while (true)
  {
    if (unique >= last_id)
    {
      *result = ADLB_DATA_ID_NULL;
      return ADLB_DATA_ERROR_LIMIT;
    }
    adlb_datum* td = table_lp_search(&tds, unique);
    if (td == NULL)
      break;
    unique += servers;
  }*/
  if (unique >= last_id)
  {
    *result = ADLB_DATA_ID_NULL;
    return ADLB_DATA_ERROR_LIMIT;
  }
  *result = unique;
  unique += servers;
  return ADLB_DATA_SUCCESS;
}

#define TYPE_NAME_INTEGER "integer"
#define TYPE_NAME_FLOAT "float"
#define TYPE_NAME_STRING "string"
#define TYPE_NAME_BLOB "blob"
#define TYPE_NAME_CONTAINER "container"
#define TYPE_NAME_MULTISET "multiset"
#define TYPE_NAME_REF "ref"
#define TYPE_NAME_FILE_REF "file_ref"
#define TYPE_NAME_STRUCT "struct"
#define TYPE_NAME_NULL "ADLB_DATA_TYPE_NULL"

struct type_entry
{
  adlb_data_type code;
  const char *name;
  size_t name_len;
};

static struct type_entry type_entries[] = {
  { ADLB_DATA_TYPE_INTEGER, TYPE_NAME_INTEGER,
    sizeof(TYPE_NAME_INTEGER) - 1 },
  { ADLB_DATA_TYPE_FLOAT, TYPE_NAME_FLOAT,
    sizeof(TYPE_NAME_FLOAT) - 1 },
  { ADLB_DATA_TYPE_STRING, TYPE_NAME_STRING,
    sizeof(TYPE_NAME_STRING) - 1 },
  { ADLB_DATA_TYPE_BLOB, TYPE_NAME_BLOB,
    sizeof(TYPE_NAME_BLOB) - 1 },
  { ADLB_DATA_TYPE_CONTAINER, TYPE_NAME_CONTAINER,
    sizeof(TYPE_NAME_CONTAINER) - 1 },
  { ADLB_DATA_TYPE_MULTISET, TYPE_NAME_MULTISET,
    sizeof(TYPE_NAME_MULTISET) - 1 },
  { ADLB_DATA_TYPE_REF, TYPE_NAME_REF,
    sizeof(TYPE_NAME_REF) - 1 },
  { ADLB_DATA_TYPE_FILE_REF, TYPE_NAME_FILE_REF,
    sizeof(TYPE_NAME_FILE_REF) - 1 },
  { ADLB_DATA_TYPE_STRUCT, TYPE_NAME_STRUCT,
    sizeof(TYPE_NAME_STRUCT) - 1 },
  { ADLB_DATA_TYPE_NULL, TYPE_NAME_NULL,
    sizeof(TYPE_NAME_NULL) - 1 },
};
static int type_entries_size = sizeof(type_entries) / sizeof(*type_entries);

/**
   Convert string representation of data type to data type number
   plus additional info
 */
adlb_code
ADLB_Data_string_totype(const char* type_string,
                        adlb_data_type* type, bool *has_extra,
                        adlb_type_extra *extra)
{
  for (int i = 0; i < type_entries_size; i++)
  {
    size_t name_len = type_entries[i].name_len;
    // Check that type names starts with prefix
    if (strncmp(type_string, type_entries[i].name, name_len) == 0)
    {
      if (type_string[name_len] == '\0')
      {
        // Exact match
        *type = type_entries[i].code;
        *has_extra = false;
        return ADLB_SUCCESS;
      }
      else
      {
        if (type_entries[i].code == ADLB_DATA_TYPE_STRUCT)
        {
          // See if of form "struct1234"
          const char *suffix = &type_string[name_len];
          char *endptr;
          long val = strtol(suffix, &endptr, 10);
          if (endptr != NULL && endptr[0] == '\0' &&
              val >= 0 && val <= INT_MAX)
          {
            // successful parse
            *type = ADLB_DATA_TYPE_STRUCT;
            *has_extra = true;
            extra->STRUCT.struct_type = (int)val;
            return ADLB_SUCCESS;
          }
          else
          {
            DEBUG("Bad struct suffix: %s", suffix);
          }
        }
        return ADLB_ERROR;
      }
    }
  }

  return ADLB_ERROR;
}

/**
   Convert given data type number to output string representation
 */
const char
*ADLB_Data_type_tostring(adlb_data_type type)
{
  switch(type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      return TYPE_NAME_INTEGER;
    case ADLB_DATA_TYPE_FLOAT:
      return TYPE_NAME_FLOAT;
    case ADLB_DATA_TYPE_STRING:
      return TYPE_NAME_STRING;
    case ADLB_DATA_TYPE_BLOB:
      return TYPE_NAME_BLOB;
    case ADLB_DATA_TYPE_CONTAINER:
      return TYPE_NAME_CONTAINER;
    case ADLB_DATA_TYPE_MULTISET:
      return TYPE_NAME_MULTISET;
    case ADLB_DATA_TYPE_REF:
      return TYPE_NAME_REF;
    case ADLB_DATA_TYPE_FILE_REF:
      return TYPE_NAME_FILE_REF;
    case ADLB_DATA_TYPE_STRUCT:
      return TYPE_NAME_STRUCT;
    case ADLB_DATA_TYPE_NULL:
      return TYPE_NAME_NULL;
    default:
      return "<invalid type>";
  }
}

const char*
xlb_data_rc_type_tostring(adlb_refcount_type rc_type)
{
  switch (rc_type)
  {
    case ADLB_READ_REFCOUNT:
      return "r";
    case ADLB_WRITE_REFCOUNT:
      return "w";
    case ADLB_READWRITE_REFCOUNT:
      return "rw";
    default:
      return "<UNKNOWN RC TYPE>";
  }
}

static void free_td_entry(adlb_datum_id id, void *val)
{
  adlb_data_code dc;
  adlb_datum *d = (adlb_datum *)val;
  if (d != NULL)
  {
    if (d->status.set)
    {
      dc = ADLB_Free_storage(&d->data, d->type);
      if (dc != ADLB_DATA_SUCCESS)
        printf("Error while freeing <%"PRId64">: %d\n", id, dc);
    }

    list_i_clear(&d->listeners);

    free(d);
  }
}

static void free_cref_entry(const void *key, size_t key_len, void *val)
{
  assert(key != NULL && val != NULL);
  struct list_l* listeners = val;
  struct list_l_item *curr;

  for (curr = listeners->head; curr != NULL; curr = curr->next)
  {
    // TODO: support binary key
    adlb_datum_id id;
    adlb_subscript sub;
    read_id_sub(key, key_len, &id, &sub);
    printf("UNFILLED CONTAINER REFERENCE <%"PRId64">[%.*s] => <%"PRId64">\n",
            id, (int)sub.length, (const char*)sub.key, curr->data);
  }
  list_l_free(listeners);
}

static void free_ix_l_entry(const void *key, size_t key_len, void *val)
{
  assert(key != NULL && val != NULL);
  struct list_i* listeners = val;
  list_i_free(listeners);
}

static void free_locked_entry(int64_t key, void *val)
{
  assert(val != NULL);
  free(val);
}

adlb_data_code
xlb_data_finalize()
{
  // First report any leaks or other problems
  report_leaks();

  // Secondly free up memory allocated in this module
  table_lp_free_callback(&tds, false, free_td_entry);

  table_bp_free_callback(&container_references, false, free_cref_entry);
  table_bp_free_callback(&container_ix_listeners, false, free_ix_l_entry);

  table_lp_free_callback(&locked, false, free_locked_entry);

  adlb_data_code dc = xlb_struct_finalize();
  DATA_CHECK(dc);
  return ADLB_DATA_SUCCESS;
}

static void
report_leaks()
{
  bool report_leaks_setting;
  getenv_boolean("ADLB_REPORT_LEAKS", false, &report_leaks_setting);

  TABLE_LP_FOREACH(&tds, item)
  {
    adlb_datum *d = item->data;
    if (d == NULL || !d->status.permanent)
    {
      if (d->status.set)
      {
        DEBUG("LEAK: %"PRId64"", item->key);
        if (report_leaks_setting)
        {
          char *repr = ADLB_Data_repr(&d->data, d->type);
          printf("LEAK DETECTED: <%"PRId64"> t:%s r:%i w:%i v:%s\n",
                item->key, ADLB_Data_type_tostring(d->type),
                d->read_refcount, d->write_refcount,
                repr);
          free(repr);
        }
      }
      else
      {
        DEBUG("UNSET VARIABLE: %"PRId64"", item->key);
        if (report_leaks_setting)
        {
          printf("UNSET VARIABLE DETECTED: <%"PRId64">\n", item->key);
        }
      }
    }
  }
}
