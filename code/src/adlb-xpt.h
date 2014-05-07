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
 */

#ifndef __ADLB_XPT_H
#define __ADLB_XPT_H

#include "adlb-defs.h"
#include "adlb_types.h"

/*
  Flush policy to use for checkpointing
 */
typedef enum {
  ADLB_NO_FLUSH,       // Don't explicitly flush
  ADLB_PERIODIC_FLUSH, // Flush checkpoints frequently
  ADLB_ALWAYS_FLUSH,   // Flush checkpoints on every write
} adlb_xpt_flush_policy;

/*
  How to persist current checkpoint
 */
typedef enum {
  // Only update in-memory index
  ADLB_NO_PERSIST,
  // Persist to checkpoint file
  ADLB_PERSIST,
  // Persist to file and flush immediately (e.g. for important data)
  ADLB_PERSIST_FLUSH, 
} adlb_xpt_persist;

/*
  Initialize checkpointing to file.
  Should be called after ADLB is initialized.

  Checkpointing is automatically finalized when ADLB is shutdown.

  filename: checkpoint filename.  If NULL, writing to file is disabled
  fp: controls the policy used for flushing checkpoint entires to disk
  max_index_val: maximum value size to store in in-memory index. Larger
      values are persisted to file and a reference stored in index.
 */
adlb_code ADLB_Xpt_init(const char *filename, adlb_xpt_flush_policy fp,
                        int max_index_val);

/*
  Finalize checkpointing to file.  If not initialized, this call has
  no effect.
 */
adlb_code ADLB_Xpt_finalize(void);

/*
  Add a checkpoint entry
  Add to in-memory index if specified
  Log to file if persist is specified, or if value too large.

  If not specified, flushing follows flushing policy.
  Result is flushed to disk if requested, or if value too large to fit
  in memory (since other nodes may need to lookup result).
 */
adlb_code ADLB_Xpt_write(const void *key, int key_len, const void *val,
                int val_len, adlb_xpt_persist persist, bool index_add);

/*
  Lookup checkpoint for key in in-memory index.
  Result is filled in upon success.  Caller is responsible for freeing
  any returned memory in result.  If a non-caller-owned pointer is returned
  (e.g. into an internal buffer), it is only valid until the next ADLB call.
  Return ADLB_SUCCESS if found, ADLB_NOTHING if not present
 */
adlb_code ADLB_Xpt_lookup(const void *key, int key_len, adlb_binary_data *result);

typedef struct {
  // True if attempted to load (may have been loaded in other process)
  bool loaded; 
  int valid; // Valid checkpoint entries loaded
  int invalid; // Invalid checkpoint entries found.
} adlb_xpt_load_rank_stats;

typedef struct {
  uint32_t ranks;
  adlb_xpt_load_rank_stats *rank_stats; // Array with one entry per rank
} adlb_xpt_load_stats;

/*
  Reload checkpoint data from file into in-memory index.
  Return error if checkpoint file appears to be invalid.
  If corrupted or partially written entries are encountered, ignore them.

  stats: info about loaded data.  Caller must free arrays.
  load_rank: rank among loaders for splitting in range [0, loaders - 1]
  loaders: total number of loaders
 */
adlb_code ADLB_Xpt_reload(const char *filename, adlb_xpt_load_stats *stats,
                          int load_rank, int loaders);

#endif // __ADLB_XPT_H
