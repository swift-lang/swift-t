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
/**
 * Functions used to maintain in-memory checkpoint index.
 */

#ifdef XLB_ENABLE_XPT
#ifndef __XLB_XPT_INDEX_H
#define __XLB_XPT_INDEX_H

#include "adlb-defs.h"
#include "adlb_types.h"

/*
  Sets up the in-memory index on servers.
 */
adlb_code xlb_xpt_index_init(void);


typedef struct 
{
  // If true, checkpoint data must be read from file.
  bool in_file;
  union {
    // Actual data
    adlb_binary_data DATA;
    // OR chunk of current checkpoint file
    struct {
      off_t val_offset;
      int val_len;
    } FILE_LOCATION;
  };
} xpt_lookup_res;

/*
  Lookup in-memory index by key.
  Return ADLB_SUCCESS on success, or ADLB_NOTHING if no matching entry.
  Return ADLB_ERROR on error.

  Caller must free any allocated binary data returned in adlb_binary_data.
 */
adlb_code xlb_xpt_index_lookup(const void *key, int key_len,
                               xpt_lookup_res *res);

/*
  Add index entry.
 */
adlb_code xlb_xpt_index_add(const void *key, int key_len, const void *val,
                           int val_len);

#endif // __XLB_XPT_INDEX_H
#endif // XLB_ENABLE_XPT
