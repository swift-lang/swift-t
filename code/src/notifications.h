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
  adlb_refc refcounts; // Refcounts to transfer
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
  int work_type;
} adlb_notif_rank;

typedef struct {
  adlb_notif_rank *notifs;
  int count;
  int size;
} adlb_notif_ranks;

/** Represent change in refcount that must be applied */
typedef struct {
  adlb_datum_id id;
  adlb_refc rc;
  
  /** If true, we don't have ownership of reference:
      must acquire before doing anything to avoid
      race condition on freeing */
  bool must_preacquire;
} xlb_refc_change;

#ifndef XLB_INDEX_REFC_CHANGES
#define XLB_INDEX_REFC_CHANGES 0
#endif

/** List of refcount changes */
typedef struct {
#if XLB_INDEX_REFC_CHANGES
  // Index changes by ID to allow merging
  table_lp index;
#endif
  xlb_refc_change *arr;
  int count;
  int size;
} xlb_refc_changes;

typedef struct {
  adlb_notif_ranks notify;
  adlb_ref_data references;
  xlb_refc_changes refcs;

  // All data that needs to be freed after notifications, e.g. subscripts
  // (may be NULL)
  void **to_free;
  int to_free_length;
  int to_free_size; // Allocated length
} adlb_notif_t;

/*
 * Initial sizes for dynamically expanded arrays
 */
#define XLB_NOTIFS_INIT_SIZE 16
#define XLB_REFS_INIT_SIZE 16
#define XLB_REFC_CHANGES_INIT_SIZE 16
#define XLB_TO_FREE_INIT_SIZE 16


static inline bool xlb_notif_ranks_empty(const adlb_notif_ranks *notif)
{
  return notif->count == 0;
}

static inline bool xlb_refc_changes_empty(const xlb_refc_changes *changes)
{
  return changes->count == 0;
}

static inline bool xlb_refs_empty(const adlb_ref_data *refs)
{
  return refs->count == 0;
}
static inline bool xlb_notif_empty(const adlb_notif_t *notif)
{
  return notif->notify.count == 0 && notif->references.count == 0 &&
         notif->refcs.count == 0;
}

#define ADLB_NO_NOTIF_RANKS { .count = 0, .size = 0, .notifs = NULL }
#define ADLB_NO_DATUMS { .count = 0, .size = 0, .data = NULL }
#define ADLB_NO_REFC_CHANGES { .size = 0, .count = 0, .arr = NULL }
#define ADLB_NO_NOTIFS { .notify = ADLB_NO_NOTIF_RANKS,  \
                         .references = ADLB_NO_DATUMS,   \
                         .refcs = ADLB_NO_REFC_CHANGES, \
                         .to_free = NULL, .to_free_length = 0, \
                         .to_free_size = 0 }


void xlb_free_notif(adlb_notif_t *notifs);
void xlb_free_ranks(adlb_notif_ranks *ranks);
void xlb_free_datums(adlb_ref_data *datums);

/*
 * expand functions:
 *  Called to enlarge array by at least to_add items
 */
adlb_code xlb_notifs_expand(adlb_notif_ranks *notifs, int to_add);
adlb_code xlb_refs_expand(adlb_ref_data *refs, int to_add);
adlb_code xlb_refc_changes_expand(xlb_refc_changes *c, int to_add);
adlb_code xlb_to_free_expand(adlb_notif_t *notifs, int to_add);

/*
   When called from server, remove any notifications that can or must
   be handled locally.  Frees memory if all removed.
 */
adlb_code
xlb_process_local_notif(adlb_notif_t *notifs);

adlb_code
xlb_notify_all(adlb_notif_t *notifs);

/*
 * Track state of prepared notifs.
 */
typedef struct {
  void *extra_data;
  struct packed_notif *packed_notifs;
  struct packed_reference *packed_refs;

  // Should we free pointers
  bool free_packed_notifs : 1;
  bool free_packed_refs : 1;
  bool free_extra_data;
} xlb_prepared_notifs;

/**
 * Processes any local notifications.
 *
 * caller_buffer: optional buffer to use instead of malloc
 * client_counts: struct to fill in with counts to send to
 *             client and pass to xlb_send_notif_work
 * prepared: internal state struct that should be passed to
 *           xlb_send_notif_work
 * must_send: set to true if caller must send the notifications with
 *            xlb_send_notif_work
 */
adlb_code
xlb_prepare_notif_work(adlb_notif_t *notifs,
        const adlb_buffer *caller_buffer,
        struct packed_notif_counts *client_counts,
        xlb_prepared_notifs *prepared, bool *must_send);

/**
 * Transfer notification work back to caller rank.
 * counts and prepared structs must be prepared with
 * xlb_prepare_notif_work
 * Caller receives w/ xlb_handle_client_notif_work or xlb_recv_notif_work
 */
adlb_code
xlb_send_notif_work(int caller, adlb_notif_t *notifs,
    const struct packed_notif_counts *counts,
    const xlb_prepared_notifs *prepared);

/*
  Receive notifications send by server, then
  process them locally.  Note that this may involve communicating
  with server, so should not be called if there are, for example,
  unmatched messages you expect to receive from somewhere
 */
adlb_code
xlb_handle_client_notif_work(const struct packed_notif_counts *counts, 
                        int to_server_rank);

/*
  notify: notify structure in valid state (init to empty, or with
          valid data)
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

static inline void xlb_notif_init(adlb_notif_rank *r,
    int rank, adlb_datum_id id, adlb_subscript subscript,
    int work_type)
{
  r->rank = rank;
  r->id = id;
  r->subscript = subscript;
  r->work_type = work_type;
}

static inline adlb_code xlb_notifs_add(adlb_notif_ranks *notifs,
        int rank, adlb_datum_id id, adlb_subscript subscript,
        int work_type)
{
  // Mark that caller should free
  if (notifs->count == notifs->size)
  {
    adlb_code ac = xlb_notifs_expand(notifs, 1);
    ADLB_CHECK(ac);
  }

  adlb_notif_rank *r = &notifs->notifs[notifs->count++];
  xlb_notif_init(r, rank, id, subscript, work_type);
  return ADLB_SUCCESS;
}

/**
 * Add a reference to the notifications
 * sub: the pointer to the subscript is retained in the notifications
 *      structure, but is not freed unless it is in the to_free list.
 */
static inline adlb_code xlb_refs_add(adlb_ref_data *refs,
      adlb_datum_id id, adlb_subscript sub, adlb_data_type type,
      const void *value, int value_len, adlb_refc refcounts)
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
static inline adlb_code xlb_refc_changes_init(xlb_refc_changes *c)
{
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
  return ADLB_SUCCESS;
}


static inline bool
xlb_refc_change_merge_existing(xlb_refc_changes *c,
    adlb_datum_id id, int read_change, int write_change,
    bool must_preacquire)
{
#if XLB_INDEX_REFC_CHANGES
  void *tmp;
  if (table_lp_search(&c->index, id, &tmp))
  {
    unsigned long change_ix = (unsigned long)tmp;
    assert(change_ix < c->count);
    xlb_refc_change *change = &c->arr[change_ix];
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
#endif
  return false;
}

static inline adlb_code xlb_refc_changes_add(xlb_refc_changes *c,
    adlb_datum_id id, int read_change, int write_change,
    bool must_preacquire)
{
  xlb_refc_change *change;
  adlb_code ac;

  // Ensure index initialized
  if (c->count == 0 ||
      !xlb_refc_change_merge_existing(c, id, read_change, write_change,
                                    must_preacquire))
  {
    // New ID, add to array
    if (c->count == c->size)
    {
      ac = xlb_refc_changes_expand(c, 1);
      ADLB_CHECK(ac);
    }

    unsigned long change_ix = (unsigned long)c->count++;
    change = &c->arr[change_ix];
    change->id = id;
    change->rc.read_refcount = read_change;
    change->rc.write_refcount = write_change;
    // If we don't own a ref, must acquire one before doing anything
    // that would cause referand to be freed
    change->must_preacquire = must_preacquire;

#if XLB_INDEX_REFC_CHANGES
    bool added = table_lp_add(&c->index, id, (void*)change_ix);
    CHECK_MSG(added, "Could not add to refcount index table");
#endif

    DEBUG("Add change: <%"PRId64"> r: %i w: %i pa: %i", change->id,
            change->rc.read_refcount, change->rc.write_refcount, 
            (int)change->must_preacquire);
  }

  return ADLB_SUCCESS;
}

static inline void xlb_refc_changes_free(xlb_refc_changes *c)
{
  if (c->arr != NULL) {
    free(c->arr);
#if XLB_INDEX_REFC_CHANGES
    table_lp_free_callback(&c->index, false, NULL);
#endif
  }
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
}

#endif // ADLB_NOTIFICATIONS_H

