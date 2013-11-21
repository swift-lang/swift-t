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
 * handlers.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 *
 *  ADLB Server: RPC handlers
 */

#ifndef HANDLERS_H
#define HANDLERS_H

#include <stdbool.h>

#include "messaging.h"

void xlb_handlers_init(void);

/**
   Just check that this is a tag known to the handlers
 */
bool xlb_handler_valid(adlb_tag tag);

adlb_code xlb_handle(adlb_tag tag, int from_rank);

/** 
   Targeted put for local target
*/
adlb_code xlb_put_targeted_local(int type, int putter, int priority,
           int answer, int target, const void* payload, int length);

void xlb_print_handler_counters(void);

adlb_code xlb_recheck_queues(void);
adlb_code xlb_recheck_parallel_queues(void);

adlb_code send_parallel_work_unit(int *workers, xlb_work_unit *wu);

#endif
