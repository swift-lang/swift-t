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
#ifdef XLB_ENABLE_XPT
#include "adlb-xpt.h"

#include "checks.h"
#include "debug.h"
#include "xpt_file.h"
#include "xpt_index.h"

/*
  Checkpoint module state
 */
static xlb_xpt_state xpt_state;
static bool xlb_xpt_initialized = false;
static adlb_xpt_flush_policy flush_policy;
static int max_index_val_bytes;

/*
  Internal functions
 */

static inline adlb_code xpt_reload_rank(const char *filename,
        xlb_xpt_read_state *read_state, adlb_buffer *buffer, uint32_t rank,
        adlb_xpt_load_rank_stats *stats);

adlb_code ADLB_Xpt_init(const char *filename, adlb_xpt_flush_policy fp,
                        int max_index_val)
{
  adlb_code rc;
  rc = xlb_xpt_init(filename, &xpt_state);
  ADLB_CHECK(rc);

  rc = xlb_xpt_index_init();
  ADLB_CHECK(rc);
  
  flush_policy = fp;
  max_index_val_bytes = max_index_val;
  xlb_xpt_initialized = true;
  return ADLB_SUCCESS;
}

adlb_code ADLB_Xpt_finalize(void)
{
  if (!xlb_xpt_initialized)
  {
    return ADLB_SUCCESS;
  }

  adlb_code rc;
  xlb_xpt_initialized = false;

  rc = xlb_xpt_close(&xpt_state);
  ADLB_CHECK(rc);

  return ADLB_SUCCESS;
}

adlb_code ADLB_Xpt_write(const void *key, int key_len, const void *val,
                int val_len, adlb_xpt_persist persist, bool index_add)
{
  assert(xlb_xpt_initialized);
  assert(key_len >= 0);
  assert(val_len >= 0);

  adlb_code rc;
  bool do_persist = persist != ADLB_NO_PERSIST;
  xpt_index_entry entry;

  if (index_add)
  {
    if (val_len > max_index_val_bytes)
    {
      // Too big for memory, must write to file
      do_persist = true;
      entry.in_file = true;
      // Fill in file location upon write
    }
    else
    {
      // Store data directly in index
      entry.in_file = false;
      entry.DATA.data = val;
      entry.DATA.caller_data = NULL;
      entry.DATA.length = val_len;
    }
  }

  if (do_persist)
  {
    off_t val_offset;
    // Must persist entry
    rc = xlb_xpt_write(key, key_len, val, val_len, &xpt_state,
                       &val_offset);
    ADLB_CHECK(rc);

    if (flush_policy == ADLB_ALWAYS_FLUSH ||
        persist == ADLB_PERSIST_FLUSH)
    {
      rc = xlb_xpt_flush(&xpt_state);
      ADLB_CHECK(rc);
    }

    if (index_add && entry.in_file)
    {
      // Must update entry
      entry.FILE_LOC.file = NULL; // NULL means current file
      entry.FILE_LOC.val_offset = val_offset;
      entry.FILE_LOC.val_len = val_len;
    }
  }

  if (index_add)
  {
    rc = xlb_xpt_index_add(key, key_len, &entry);
    ADLB_CHECK(rc);
  }
  return ADLB_SUCCESS;
}

adlb_code ADLB_Xpt_lookup(const void *key, int key_len, adlb_binary_data *result)
{
  assert(xlb_xpt_initialized);
  assert(key != NULL);
  assert(key_len >= 0);
  assert(result != NULL);

  adlb_code rc;
  xpt_index_entry res;

  rc = xlb_xpt_index_lookup(key, key_len, &res);
  if (rc == ADLB_NOTHING)
  {
    return ADLB_NOTHING;
  }
  ADLB_CHECK(rc);

  if (res.in_file)
  {
    // Allocate buffer that caller should free
    int val_len = res.FILE_LOC.val_len;
    result->data = result->caller_data = malloc((size_t)val_len);
    result->length = val_len;
    CHECK_MSG(result->data != NULL, "Could not allocate buffer");

    rc = xlb_xpt_read_val(res.FILE_LOC.file, res.FILE_LOC.val_offset,
                  val_len, &xpt_state, result->caller_data);
    ADLB_CHECK(rc);
  }
  else
  {
    *result = res.DATA;
  }
  return ADLB_SUCCESS;
}

/*
  Open checkpoint file for reading and slurp up all records
  into our checkpoint index.
  TODO: will probably need to support some kind of filtering
 */
adlb_code ADLB_Xpt_reload(const char *filename, adlb_xpt_load_stats *stats)
{
  CHECK_MSG(stats != NULL, "Must provide stats argument");

  adlb_code rc;
  xlb_xpt_read_state read_state;
  adlb_buffer buffer = { .data = NULL };

  rc = xlb_xpt_open_read(filename, &read_state);
  if (rc != ADLB_SUCCESS)
    goto cleanup_exit;

  // TODO: arbitrary buffer size
  //      we probably want min(<some sensible amount>,
  //                           max_index_val_bytes + key_size);
  buffer.length = 4 * 1024 * 1024;
  buffer.data = malloc((size_t)buffer.length);
  CHECK_MSG(buffer.data != NULL, "Error allocating buffer");

  const int ranks = read_state.ranks;
  stats->ranks = ranks;
  stats->rank_stats = malloc(sizeof(stats->rank_stats[0]) * ranks);
  CHECK_MSG(stats->rank_stats != NULL, "Out of memory");
  for (int i = 0; i < ranks; i++)
  {
    stats->rank_stats[i].loaded = false;
  }
  // TODO: how to split ranks in checkpoint among ranks in current
  // cluster.
  for (uint32_t rank = 0; rank < ranks; rank++)
  {
    DEBUG("Reloading checkpoints from %s for rank %i\n", filename, rank);
    adlb_xpt_load_rank_stats *rstats = &stats->rank_stats[rank];
    rc = xpt_reload_rank(filename, &read_state, &buffer, rank, rstats);
    if (rc != ADLB_SUCCESS)
    {
      // Continue to next rank upon error
      ERR_PRINTF("Error reloading records for rank %"PRId32"\n", rank);
    }
    DEBUG("Done reloading checkpoints from %s for rank %i. "
          "Valid: %i Invalid: %i\n", filename, rank,
          rstats->valid, rstats->invalid);
  }
  
  rc = xlb_xpt_close_read(&read_state);
  if (rc != ADLB_SUCCESS)
    goto cleanup_exit;

  rc = ADLB_SUCCESS;
cleanup_exit:
  if (buffer.data != NULL)
  {
    free(buffer.data);
  }
  return rc;
}

/*
  Read the checkpoint data for the specified rank into the in-memory
  index.  This function may realloc the provided buffer.
 */
static inline adlb_code xpt_reload_rank(const char *filename,
        xlb_xpt_read_state *read_state, adlb_buffer *buffer, uint32_t rank,
        adlb_xpt_load_rank_stats *stats)
{
  stats->loaded = true;
  stats->valid = 0;
  stats->invalid = 0;

  adlb_code rc;
  rc = xlb_xpt_read_select(read_state, rank);
  ADLB_CHECK(rc);

  // Read all records for this rank
  while (true)
  {
    void *key_ptr, *val_ptr;
    int key_len, val_len;
    off_t val_offset;
    rc = xlb_xpt_read(read_state, buffer, &key_len, &key_ptr,
                      &val_len, &val_ptr, &val_offset);
    if (rc == ADLB_RETRY)
    {
      // Allocate larger buffer to fit
      buffer->length = key_len;
      buffer->data = realloc(buffer->data, (size_t)buffer->length);
      CHECK_MSG(buffer->data != NULL, "Error allocating buffer");
      rc = xlb_xpt_read(read_state, buffer, &key_len, &key_ptr,
                        &val_len, &val_ptr, &val_offset);
    }

    if (rc == ADLB_DONE)
    {
      // ADLB_DONE indicates last valid record for rank
      return ADLB_SUCCESS;
    }
    else if (rc == ADLB_NOTHING)
    {
      DEBUG("Invalid record");
      // ADLB_NOTHING indicates corrupted record
      stats->invalid++;
      // Skip this record
      continue;
    }
    else if (rc != ADLB_SUCCESS)
    {
      DEBUG("Unrecoverable error reading checkpoints for rank");
      stats->invalid++;
      return ADLB_ERROR;
    }
    else if (val_len > ADLB_XPT_MAX)
    {
      ERR_PRINTF("Checkpoint entry loaded from file "
          "bigger than ADLB_XPT_MAX: %i vs %i\n", val_len, ADLB_XPT_MAX);
      stats->invalid++;
      return ADLB_ERROR;
    }

    xpt_index_entry entry;
    if (val_len > max_index_val_bytes)
    {
      entry.in_file = true;
      entry.FILE_LOC.file = filename;
      entry.FILE_LOC.val_offset = val_offset;
      entry.FILE_LOC.val_len = val_len;
    }
    else
    {
      entry.in_file = false;
      entry.DATA.data = val_ptr;
      entry.DATA.caller_data = NULL;
      entry.DATA.length = val_len;
    }
    rc = xlb_xpt_index_add(key_ptr, key_len, &entry);
    CHECK_MSG(rc == ADLB_SUCCESS, "Error loading checkpoint into index");
    DEBUG("Loaded checkpoint for rank %i val_len: %i in_file: %s",
          rank, (int)val_len, entry.in_file ? "true" : "false");

    // If we made it this far, should be valid
    stats->valid++;
  }
}

#endif // XLB_ENABLE_XPT
