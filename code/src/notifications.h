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
  // id is implied
  adlb_subscript subscript; // Optional subscript
} adlb_notif_rank;

typedef struct {
  int count;
  adlb_notif_rank *notifs;
} adlb_notif_ranks;

typedef struct {
  adlb_notif_ranks notify;
  adlb_ref_data references;

  // All data that needs to be freed after notifications, e.g. subscripts
  // (may be NULL)
  void **to_free;
  int to_free_length;
  int to_free_size; // Allocated length
} adlb_notif_t;

#define ADLB_NO_NOTIF_RANKS { .count = 0, .notifs = NULL }
#define ADLB_NO_DATUMS { .count = 0, .data = NULL }
#define ADLB_NO_NOTIFS { .notify = ADLB_NO_NOTIF_RANKS,  \
                         .references = ADLB_NO_DATUMS,   \
                         .to_free = NULL, .to_free_length = 0, \
                         .to_free_size = 0 }

void xlb_free_notif(adlb_notif_t *notifs);
void xlb_free_ranks(adlb_notif_ranks *ranks);
void xlb_free_datums(adlb_ref_data *datums);

adlb_code xlb_set_refs(const adlb_ref_data *refs);

adlb_code
xlb_set_ref_and_notify(adlb_datum_id id, const void *value, int length,
                         adlb_data_type type);

adlb_code
xlb_close_notify(adlb_datum_id id, const adlb_notif_ranks *ranks);

/*
   When called from server, remove any notifications that can be handled
   locally.  Frees memory if all removed.
 */
adlb_code
xlb_process_local_notif(adlb_datum_id id, adlb_notif_ranks *ranks);

adlb_code
xlb_notify_all(const adlb_notif_t *notifs, adlb_datum_id id);

#endif // ADLB_NOTIFICATIONS_H

