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


/*
 * notifications.h
 *
 *  Created on: July 2, 2013
 *      Author: armstrong
 *
 * Data structures and functions for sending notifications
 */

#ifndef ADLB_NOTIFICATIONS_H
#define ADLB_NOTIFICATIONS_H

#include "adlb-defs.h"
#include "checks.h"
#include "messaging.h"

#include <table_lp.h>

/** If ADLB_CLIENT_NOTIFIES is true, client is responsible for
    notifying others of closing, otherwise the server does it */
#ifndef ADLB_CLIENT_NOTIFIES
#define ADLB_CLIENT_NOTIFIES (true)
#endif

/*
  Represent that value should be stored to id
 */
typedef struct {
  adlb_datum_id id; // ID to set
  adlb_subscript subscript; // Subscript of ID to set
  adlb_refcounts refcounts; // Refcounts to transfer
  adlb_data_type type;
  // Data to set it to:
  const void *value;
  int value_len;
} adlb_ref_datum;

typedef struct {
  adlb_ref_datum *data;
  int count;
  int size;
} adlb_ref_data;

/*
  Represent that we need to notify that rank that <id> or 
  <id>[subscript] was set
 */
typedef struct {
  adlb_datum_id id;
  adlb_subscript subscript; // Optional subscript
  int rank;
} adlb_notif_rank;

typedef struct {
  adlb_notif_rank *notifs;
  int count;
  int size;
} adlb_notif_ranks;

/** Represent change in refcount that must be applied */
typedef struct {
  adlb_datum_id id;
  adlb_refcounts rc;
  
  /** If true, we don't have ownership of reference:
      must acquire before doing anything to avoid
      race condition on freeing */
  bool must_preacquire;
} xlb_rc_change;

/** List of refcount changes */
typedef struct {
  // Index changes by ID to allow merging
  table_lp index;
  xlb_rc_change *arr;
  int count;
  int size;
} xlb_rc_changes;

#define XLB_RC_CHANGES_INIT_SIZE 16

typedef struct {
  adlb_notif_ranks notify;
  adlb_ref_data references;
  xlb_rc_changes rc_changes;

  // All data that needs to be freed after notifications, e.g. subscripts
  // (may be NULL)
  void **to_free;
  int to_free_length;
  size_t to_free_size; // Allocated length
} adlb_notif_t;

static inline bool xlb_notif_ranks_empty(adlb_notif_ranks *notif)
{
  return notif->count == 0;
}

static inline bool xlb_notif_empty(adlb_notif_t *notif)
{
  return notif->notify.count == 0 && notif->references.count == 0 &&
         notif->rc_changes.count == 0;
}

#define ADLB_NO_NOTIF_RANKS { .count = 0, .size = 0, .notifs = NULL }
#define ADLB_NO_DATUMS { .count = 0, .size = 0, .data = NULL }
#define ADLB_NO_RC_CHANGES { .size = 0, .count = 0, .arr = NULL }
#define ADLB_NO_NOTIFS { .notify = ADLB_NO_NOTIF_RANKS,  \
                         .references = ADLB_NO_DATUMS,   \
                         .rc_changes = ADLB_NO_RC_CHANGES, \
                         .to_free = NULL, .to_free_length = 0, \
                         .to_free_size = 0 }


void xlb_free_notif(adlb_notif_t *notifs);
void xlb_free_ranks(adlb_notif_ranks *ranks);
void xlb_free_datums(adlb_ref_data *datums);

adlb_code xlb_notifs_expand(adlb_notif_ranks *notifs, int to_add);
adlb_code xlb_to_free_expand(adlb_notif_t *notifs, int to_add);
adlb_code xlb_refs_expand(adlb_ref_data *refs, int to_add);

/*
   When called from server, remove any notifications that can or must
   be handled locally.  Frees memory if all removed.
 */
adlb_code
xlb_process_local_notif(adlb_notif_t *notifs);

adlb_code
xlb_notify_all(adlb_notif_t *notifs);

/**
 * Transfer notification work back to caller rank.
 * Caller receives w/ xlb_handle_client_notif_work or xlb_recv_notif_work
 * Finish filling in provided response header with info about
 * notifications, then send to caller including additional
 * notification work.
 * 
 * response/response_len: pointer to response struct to be sent
 * inner_struct: struct inside outer struct to be updated before sending
 * use_xfer: if true, use xfer buffer as scratch space
 */
adlb_code
xlb_send_notif_work(int caller,
        void *response, size_t response_len,
        struct packed_notif_counts *inner_struct,
        adlb_notif_t *notifs, bool use_xfer);

/*
  Receive notifications send by server, then
  process them locally
 */
adlb_code
xlb_handle_client_notif_work(const struct packed_notif_counts *counts, 
                        int to_server_rank);

/*
  notify: notify structure initialzied to empty
 */
adlb_code
xlb_recv_notif_work(const struct packed_notif_counts *counts,
    int to_server_rank, adlb_notif_t *notifs);

static inline adlb_code xlb_to_free_add(adlb_notif_t *notifs, void *data)
{
  // Mark that caller should free
  if (notifs->to_free_length == notifs->to_free_size)
  {
    adlb_code ac = xlb_to_free_expand(notifs, 1);
    ADLB_CHECK(ac);
  }
  notifs->to_free[notifs->to_free_length++] = data;
  return ADLB_SUCCESS;
}

static inline adlb_code xlb_notifs_add(adlb_notif_ranks *notifs,
        int rank, adlb_datum_id id, adlb_subscript subscript)
{
  // Mark that caller should free
  if (notifs->count == notifs->size)
  {
    adlb_code ac = xlb_notifs_expand(notifs, 1);
    ADLB_CHECK(ac);
  }

  adlb_notif_rank *r = &notifs->notifs[notifs->count++];
  r->rank = rank;
  r->id = id;
  r->subscript = subscript;

  return ADLB_SUCCESS;
}

/**
 * Add a reference to the notifications
 * sub: the pointer to the subscript is retained in the notifications
 *      structure, but is not freed unless it is in the to_free list.
 */
static inline adlb_code xlb_refs_add(adlb_ref_data *refs,
      adlb_datum_id id, adlb_subscript sub, adlb_data_type type,
      const void *value, int value_len, adlb_refcounts refcounts)
{
  // Mark that caller should free
  if (refs->count == refs->size)
  {
    adlb_code ac = xlb_refs_expand(refs, 1);
    ADLB_CHECK(ac);
  }

  adlb_ref_datum *d = &refs->data[refs->count++];
  d->id = id;
  d->subscript = sub;
  d->type = type;
  d->value = value;
  d->value_len = value_len;
  d->refcounts = refcounts;

  return ADLB_SUCCESS;
}

// Inline functions
static inline adlb_code xlb_rc_changes_init(xlb_rc_changes *c)
{
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
  return ADLB_SUCCESS;
}

static inline adlb_code xlb_rc_changes_expand(xlb_rc_changes *c,
                                              int to_add)
{
  if (c->arr != NULL &&
      c->count + to_add <= c->size)
  {
    // Big enough
    return ADLB_SUCCESS;
  } else {
    int new_size;
    if (c->arr == NULL)
    {
      new_size = XLB_RC_CHANGES_INIT_SIZE;
    }
    else
    {
      new_size = c->size * 2;
      if (new_size < c->count + to_add)
      {
        new_size = c->count + to_add;
      }
    }
    void *new_arr = realloc(c->arr, (size_t)new_size * sizeof(c->arr[0]));
    CHECK_MSG(new_arr != NULL, "Could not alloc array");

    // Init index, use 1.0 load factor so realloced at same pace as array
    if (!table_lp_init_custom(&c->index, new_size, 1.0))
    {
      ERR_PRINTF("Could not alloc table");
      free(new_arr);
      return ADLB_ERROR;
    }

    c->arr = new_arr;
    c->size = new_size;
    return ADLB_SUCCESS;
  }
}

static inline bool
xlb_rc_change_merge_existing(xlb_rc_changes *c,
    adlb_datum_id id, int read_change, int write_change,
    bool must_preacquire)
{
  void *tmp;
  if (table_lp_search(&c->index, id, &tmp))
  {
    unsigned long change_ix = (unsigned long)tmp;
    assert(change_ix < c->count);
    xlb_rc_change *change = &c->arr[change_ix];
    assert(change->id == id);
    change->rc.read_refcount += read_change;
    change->rc.write_refcount += write_change;
    // Check to see if we still need to preacquire
    change->must_preacquire =
      (change->must_preacquire || must_preacquire) &&
      (change->rc.read_refcount > 0 || change->rc.write_refcount > 0);
    // NOTE, this might bring both refcounts to zero.  We handle this
    // later, when processing local refcounts
    return true;
  }
  return false;
}

static inline adlb_code xlb_rc_changes_add(xlb_rc_changes *c,
    adlb_datum_id id, int read_change, int write_change,
    bool must_preacquire)
{
  xlb_rc_change *change;
  adlb_code ac;

  // Ensure index initialized
  if (c->count == 0 ||
      !xlb_rc_change_merge_existing(c, id, read_change, write_change,
                                    must_preacquire))
  {
    // New ID, add to array
    ac = xlb_rc_changes_expand(c, 1);
    ADLB_CHECK(ac);

    unsigned long change_ix = c->count++;
    change = &c->arr[change_ix];
    change->id = id;
    change->rc.read_refcount = read_change;
    change->rc.write_refcount = write_change;
    // If we don't own a ref, must acquire one before doing anything
    // that would cause referand to be freed
    change->must_preacquire = must_preacquire;

    bool added = table_lp_add(&c->index, id, (void*)change_ix);
    CHECK_MSG(added, "Could not add to refcount index table");

    DEBUG("Add change: <%"PRId64"> r: %i w: %i pa: %i", change->id,
            change->rc.read_refcount, change->rc.write_refcount, 
            (int)change->must_preacquire);
  }

  return ADLB_SUCCESS;
}

static inline void xlb_rc_changes_free(xlb_rc_changes *c)
{
  if (c->arr != NULL) {
    free(c->arr);
    table_lp_free_callback(&c->index, false, NULL);
  }
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
}

#endif // ADLB_NOTIFICATIONS_H

