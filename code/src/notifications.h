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
  adlb_data_type type;
  // Data to set it to:
  const void *value;
  int value_len;
} adlb_ref_datum;

typedef struct {
  int count;
  adlb_ref_datum *data;
} adlb_ref_data;

/*
  Represent that we need to notify that rank that <id> or 
  <id>[subscript] was set
 */
typedef struct {
  int rank;
  // TODO: need to make id non-implied
  // id is implied
  adlb_subscript subscript; // Optional subscript
} adlb_notif_rank;

typedef struct {
  int count;
  adlb_notif_rank *notifs;
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
  return notif->notify.count == 0 && notif->references.count != 0 &&
         notif->rc_changes.count == 0;
}

#define ADLB_NO_NOTIF_RANKS { .count = 0, .notifs = NULL }
#define ADLB_NO_DATUMS { .count = 0, .data = NULL }
#define ADLB_NO_RC_CHANGES { .size = 0, .count = 0, .arr = NULL }
#define ADLB_NO_NOTIFS { .notify = ADLB_NO_NOTIF_RANKS,  \
                         .references = ADLB_NO_DATUMS,   \
                         .rc_changes = ADLB_NO_RC_CHANGES, \
                         .to_free = NULL, .to_free_length = 0, \
                         .to_free_size = 0 }


void xlb_free_notif(adlb_notif_t *notifs);
void xlb_free_ranks(adlb_notif_ranks *ranks);
void xlb_free_datums(adlb_ref_data *datums);

/*
   When called from server, remove any notifications that can or must
   be handled locally.  Frees memory if all removed.
 */
adlb_code
xlb_process_local_notif(adlb_datum_id id, adlb_notif_t *notifs);

adlb_code
xlb_notify_all(adlb_notif_t *notifs, adlb_datum_id id);

/**
 * Transfer notification work back to caller rank.
 * Caller should receive with recv_notification_work.
 * Finish filling in provided response header with info about
 * notifications, then send to caller including additional
 * notification work.
 * 
 * response/response_len: pointer to response struct to be sent
 * inner_struct: struct inside outer struct to be updated before sending
 * use_xfer: if true, use xfer buffer as scratch space
 */
adlb_code
send_notification_work(int caller, adlb_datum_id id,
        void *response, size_t response_len,
        struct packed_notif_counts *inner_struct,
        adlb_notif_t *notifs, bool use_xfer);

/*
  notify: notify structure initialzied to empty
 */
adlb_code
recv_notification_work(adlb_datum_id id,
    const struct packed_notif_counts *counts, int to_server_rank,
    adlb_notif_t *not);

adlb_code
xlb_to_free_expand(adlb_notif_t *notify, int to_add);

// Inline functions
static inline adlb_code xlb_rc_changes_init(xlb_rc_changes *c)
{
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
  return ADLB_SUCCESS;
}

static inline adlb_code xlb_to_free_add(adlb_notif_t *notify, void *data)
{
  // Mark that caller should free
  if (notify->to_free_length == notify->to_free_size)
  {
    adlb_code ac = xlb_to_free_expand(notify, 1);
    ADLB_CHECK(ac);
  }
  notify->to_free[notify->to_free_length++] = data;
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
    c->arr = new_arr;
    c->size = new_size;
    return ADLB_SUCCESS;
  }
}

static inline void xlb_rc_changes_free(xlb_rc_changes *c)
{
  if (c->arr != NULL) {
    free(c->arr);
  }
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
}

#endif // ADLB_NOTIFICATIONS_H

