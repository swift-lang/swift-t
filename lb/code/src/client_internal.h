/*
 * Copyright 2014 University of Chicago and Argonne National Laboratory
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
  Internal definitions for client side functions that should not
  be exposed as part of public ADLB interface.
 */

#ifndef __XLB_CLIENT_INTERNAL_H
#define __XLB_CLIENT_INTERNAL_H

#include "adlb-defs.h"
#include "adlb-types.h"
#include "notifications.h"

adlb_code
xlb_refcount_incr(adlb_datum_id id, adlb_refc change,
                    adlb_notif_t *notifs);

adlb_code
xlb_store(adlb_datum_id id, adlb_subscript subscript, adlb_data_type type,
            const void *data, size_t length, adlb_refc refcount_decr,
            adlb_refc store_refcounts, adlb_notif_t *notifs);

#endif // __XLB_CLIENT_INTERNAL_H
