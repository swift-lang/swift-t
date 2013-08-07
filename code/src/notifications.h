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


typedef struct {
  int count;
  adlb_datum_id *ids;
} adlb_datums;

typedef struct {
  int count;
  int *ranks;
} adlb_ranks;

typedef struct {
  adlb_ranks close_notify;
  adlb_ranks insert_notify;
  adlb_datums references;
} adlb_notif_t;

#define ADLB_NO_RANKS { .count = 0, .ranks = NULL }
#define ADLB_NO_DATUMS { .count = 0, .ids = NULL }
#define ADLB_NO_NOTIFS { .close_notify = ADLB_NO_RANKS,  \
                         .insert_notify = ADLB_NO_RANKS, \
                         .references = ADLB_NO_DATUMS}

void free_adlb_notif(adlb_notif_t *notifs);
void free_adlb_ranks(adlb_ranks *ranks);
void free_adlb_datums(adlb_datums *datums);

adlb_code set_references(adlb_datum_id *refs, int refs_count,
                         const char *value, int value_len,
                         adlb_data_type type);

adlb_code
set_reference_and_notify(adlb_datum_id id, const void *value, int length,
                         adlb_data_type type);

adlb_code
close_notify(adlb_datum_id id, const char *subscript,
                   int* ranks, int count);

/*
   When called from server, remove any notifications that can be handled
   locally.  Frees memory if all removed.
 */
adlb_code
process_local_notifications(adlb_datum_id id, const char *subscript,
                            adlb_ranks *ranks);

adlb_code
notify_all(const adlb_notif_t *notifs, adlb_datum_id id,
           const char *subscript, const void *value, int value_len,
           adlb_data_type value_type);

#endif // ADLB_NOTIFICATIONS_H

