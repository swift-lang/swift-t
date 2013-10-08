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
 * Application interface for creating and looking up checkpoints.
 * TODO: this is just a sketch now
 * TODO: how to flush/specify flush policy
 */

#ifdef XLB_ENABLE_XPT
#ifndef __ADLB_XPT_H
#define __ADLB_XPT_H

#include "adlb-defs.h"
#include "adlb_types.h"

/*
  Flush policy to use for checkpointing
 */
typedef enum {
  NO_FLUSH,       // Don't explicitly flush
  PERIODIC_FLUSH, // Flush checkpoints frequently
  ALWAYS_FLUSH,   // Flush checkpoints on every write
} adlb_xpt_flush_policy;

/*
  How to persist current checkpoint
 */
typedef enum {
  // Only update in-memory index
  NO_PERSIST,
  // Persist to checkpoint file
  PERSIST,
  // Persist to file and flush immediately (e.g. for important data)
  PERSIST_FLUSH, 
} adlb_xpt_persist;

/*
  Initialize checkpointing to file.
  fp: controls the policy used for flushing checkpoint entires to disk
  max_index_val: maximum value size to store in in-memory index. Larger
      values are persisted to file and a reference stored in index.
 */
adlb_code adlb_xpt_init(const char *filename, adlb_xpt_flush_policy fp,
                        int max_index_val);

/*
  Add a checkpoint to in-memory index.
  Log to file if persist is specified, or if value too large.

  If not specified, flushing follows flushing policy.
  Result is flushed to disk if requested, or if value too large to fit
  in memory (since other nodes may need to lookup result).
 */
adlb_code adlb_xpt_write(const void *key, int key_len, const void *val,
                        int val_len, adlb_xpt_persist persist);

/*
  Lookup checkpoint for key in in-memory index.
  Result is filled in upon success.  Caller is responsible for freeing
  any returned memory in result.
  Return ADLB_SUCCESS if found, ADLB_NOTHING if not present
 */
adlb_code adlb_xpt_lookup(const void *key, int key_len, adlb_binary_data *result);

/*
  Reload checkpoint data from file into in-memory index.
  Return error if checkpoint file appears to be invalid.
  If corrupted or partially written entries are encountered, ignore them.
 */
adlb_code adlb_xpt_reload(const char *filename);

#endif // __ADLB_XPT_H
#endif // XLB_ENABLE_XPT
