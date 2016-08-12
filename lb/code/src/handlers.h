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
static inline bool xlb_handler_valid(adlb_tag tag);

static inline adlb_code xlb_handle(adlb_tag tag, int from_rank);

/**
  Put for already-allocated work unit
 */
adlb_code
xlb_put_work_unit(xlb_work_unit *work);

/** 
   Targeted put for local target
*/
adlb_code xlb_put_targeted_local(int type, int putter,
           int answer, int target, adlb_put_opts opts,
           const void* payload, int length);

void xlb_print_handler_counters(void);

adlb_code xlb_recheck_queues(bool single, bool parallel);

/*
  Inlined functions (performance-critical to server loop)
 */
#include "mpe-tools.h"

/** Type definition of all handler functions */
typedef adlb_code (*xlb_handler)(int caller);

/** Maximal number of handlers that may be registered */
#define XLB_MAX_HANDLERS XLB_MAX_TAGS

extern xlb_handler xlb_handlers[];
extern int64_t xlb_handler_counters[];

static inline bool
xlb_handler_valid(adlb_tag tag)
{
// These comparisons should always be true:  
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wtautological-compare"
  return (tag < XLB_MAX_HANDLERS) &&
         (xlb_handlers[tag] != NULL);
#pragma clang diagnostic pop
}

static inline adlb_code
xlb_handle(adlb_tag tag, int caller)
{
  ADLB_CHECK_MSG(xlb_handler_valid(tag), "handle(): invalid tag: %i (%s)",
            tag, xlb_get_tag_name(tag));
  DEBUG("handle: caller=%i %s", caller, xlb_get_tag_name(tag));

  MPE_LOG(xlb_mpe_svr_busy_start);

  if (xlb_s.perfc_enabled)
  {
    xlb_handler_counters[tag]++;
  }

  // Call handler:
  adlb_code result = xlb_handlers[tag](caller);

  MPE_LOG(xlb_mpe_svr_busy_end);

  return result;
}

#endif
