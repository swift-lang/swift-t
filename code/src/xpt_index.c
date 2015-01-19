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
#include "xpt_index.h"

#include <stdio.h>

#include "adlb.h"
#include "checks.h"
#include "common.h"
#include "data.h"
#include "jenkins-hash.h"
#include "messaging.h"
#include "table.h"

static bool xpt_index_init = false;

static adlb_datum_id xpt_index_start;

static inline adlb_datum_id id_for_rank(int comm_rank);
static inline adlb_datum_id id_for_server(int server_num);
static inline adlb_datum_id id_for_hash(uint32_t key_hash);
static inline uint32_t calc_hash(const void *data, size_t length);

adlb_code xlb_xpt_index_init(void)
{
  adlb_data_code dc;

  dc = xlb_data_system_reserve(xlb_servers, &xpt_index_start);
  ADLB_DATA_CHECK(dc);

  if (xlb_am_server)
  {
    // setup checkpoint index using sharded container on servers
    adlb_datum_id container_id = id_for_rank(xlb_comm_rank);

    // Check that the calculation is valid
    assert(ADLB_Locate(container_id) == xlb_comm_rank);
    assert(container_id >= xpt_index_start);
    assert(container_id < xpt_index_start + xlb_servers);

    DEBUG("server %i xpt container "ADLB_PRID, xlb_comm_rank,
          ADLB_PRID_ARGS(container_id, ADLB_DSYM_NULL));

    adlb_type_extra extra;
    extra.CONTAINER.key_type = ADLB_DATA_TYPE_BLOB;
    extra.CONTAINER.val_type = ADLB_DATA_TYPE_BLOB,
    extra.valid = true;
    adlb_create_props props = { .read_refcount = 1, .write_refcount = 1,
                                .permanent = true };
    dc = xlb_data_create(container_id, ADLB_DATA_TYPE_CONTAINER, &extra,
                         &props);
    ADLB_DATA_CHECK(dc);
  }

  // Wait to ensure that all containers created before exiting
  BARRIER();

  xpt_index_init = true;
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_index_lookup(const void *key, size_t key_len,
                               xpt_index_entry *res)
{
  assert(xpt_index_init);
  assert(key != NULL);
  adlb_datum_id id = id_for_hash(calc_hash(key, key_len));
  adlb_subscript subscript = { .key = key, .length = key_len };

  adlb_retrieve_refc refcounts = ADLB_RETRIEVE_NO_REFC;

  void *buffer = xlb_xfer; 
  adlb_data_type type;
  size_t length;
  adlb_code rc = ADLB_Retrieve(id, subscript, refcounts, &type,
                               buffer, &length);
  CHECK_MSG(rc == ADLB_SUCCESS, "Error looking up checkpoint in "
            "container %"PRId64, id);
  if (rc == ADLB_NOTHING)
  {
    // Not present
    return ADLB_NOTHING;
  }
  CHECK_MSG(length >= 1, "Checkpoint index val too small: %zu", length);

  // Type flag goes at end of buffer
  char in_file_flag = ((char*)buffer)[length - 1];
  if (in_file_flag != 0)
  {
    res->in_file = true;
    xpt_file_loc *res_file = &res->FILE_LOC;
    
    // Write info to binary buffer
    char *pos = (char*)buffer;
    size_t filename_len;
    CHECK_MSG(length >= sizeof(filename_len), "Buffer not large enough "
            "for filename len: %zu v %zu", length, sizeof(filename_len));
    MSG_UNPACK_BIN(pos, &filename_len);

    // Check buffer was expected size (members plus in_file byte)
    size_t exp_length = sizeof(filename_len) + filename_len +
        sizeof(res_file->val_offset) + sizeof(res_file->val_len) + 1;
    CHECK_MSG(length == exp_length, "Buffer not expected size: %zu vs %zu",
              length, exp_length);

    // Extract filename if needed
    if (filename_len == 0)
    {
      res_file->file = NULL;
    }
    else
    {
      res_file->file = malloc(filename_len + 1);
      CHECK_MSG(res_file->file != NULL, "Error allocating filename");
      memcpy(res_file->file, pos, filename_len);
      res_file->file[filename_len] = '\0';
      pos += filename_len;
    }

    MSG_UNPACK_BIN(pos, &res_file->val_offset);
    MSG_UNPACK_BIN(pos, &res_file->val_len);
    pos++; // in_file byte
    assert(pos - (char*)buffer == exp_length);
  }
  else
  {
    res->in_file = false;
    adlb_binary_data *d = &res->DATA;
    d->length = length - 1; // Account for flag
    d->data = buffer;
    // Determine whether caller owns buffer
    d->caller_data = NULL;
  }
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_index_add(const void *key, size_t key_len,
                            const xpt_index_entry *entry)
{
  assert(xpt_index_init);
  assert(key != NULL);

  // Using xlb_xfer limits the checkpoint size to ADLB_XPT_MAX ==
  // ADLB_DATA_MAX - 1
  // NOTE: assuming that ADLB_Store doesn't use xlb_xfer
  assert(ADLB_XPT_MAX <= ADLB_DATA_MAX - 1);

  const void *data; // Pointer to binary repr
  size_t data_len; // Length of data minus flag
  if (entry->in_file)
  {
    // Write info to binary buffer
    char *xfer_pos = xlb_xfer;
    size_t filename_len = entry->FILE_LOC.file != NULL ?
            strlen(entry->FILE_LOC.file) : 0;
    MSG_PACK_BIN(xfer_pos, filename_len);
    if (entry->FILE_LOC.file != NULL)
    {
      memcpy(xfer_pos, entry->FILE_LOC.file, filename_len);
      xfer_pos += filename_len;
    }
    MSG_PACK_BIN(xfer_pos, entry->FILE_LOC.val_offset);
    MSG_PACK_BIN(xfer_pos, entry->FILE_LOC.val_len);

    *xfer_pos = (char) 1; // File flag
    xfer_pos++;

    data = xlb_xfer;
    data_len = (size_t) (xfer_pos - xlb_xfer);
    assert(data_len <= ADLB_XPT_MAX); // Should certainly be smaller
  }
  else
  {
    CHECK_MSG(entry->DATA.length <= ADLB_XPT_MAX, 
      "Checkpoint data too long: %zu vs. %llu", key_len, ADLB_XPT_MAX);
    // Set file flag
    memcpy(xlb_xfer, entry->DATA.data, entry->DATA.length);
    xlb_xfer[entry->DATA.length] = (char)0; // file flag

    data = xlb_xfer;
    data_len = entry->DATA.length + 1;
  }
  adlb_refc refcounts = ADLB_NO_REFC;
  adlb_datum_id id = id_for_hash(calc_hash(key, key_len));
  adlb_subscript subscript = { .key = key, .length = key_len };
  assert(data_len <= INT_MAX);
  adlb_code rc = ADLB_Store(id, subscript, ADLB_DATA_TYPE_BLOB,
                            data, data_len, refcounts, ADLB_NO_REFC);
  
  // Handle duplicate key gracefully: it is possible for the same
  //       function to be recomputed, and we need to handle it!
  CHECK_MSG(rc == ADLB_SUCCESS || rc == ADLB_REJECTED,
            "Error storing checkpoint entry");

  return ADLB_SUCCESS;
}

/*
  Get the checkpoint container ID for a given server rank.
 */
static inline adlb_datum_id id_for_rank(int comm_rank)
{
  // Servers come after other ranks
  int server_num = comm_rank - (xlb_comm_size - xlb_servers);
  return id_for_server(server_num);
}

static inline adlb_datum_id id_for_server(int server_num)
{
  assert(server_num >= 0 && server_num < xlb_servers);
  assert(xpt_index_start <= -xlb_servers);
  // Will be in range [xpt_index_start, xpt_index_start + xlb_servers)
  // ADLB_Locate must map this id to the right server

  // Compensate for fact that xpt_index_start may not be multiple of
  // xlb_servers
  // -xlb_servers < offset <= 0
  adlb_datum_id offset = xpt_index_start % xlb_servers;

  adlb_datum_id shift = (xlb_servers - (server_num + offset)) % xlb_servers;

  return xpt_index_start + shift;
}

/*
  Work out checkpoint container ID given key hash
 */
__attribute__((always_inline))
static inline adlb_datum_id id_for_hash(uint32_t key_hash)
{
  // Must be negative number in range
  // [xpt_index_start, xpt_index_start + xlb_serversservers)
  return (int32_t)(key_hash % (uint32_t)xlb_servers) + xpt_index_start;
}

__attribute__((always_inline))
static inline uint32_t calc_hash(const void *data, size_t length)
{
  return bj_hashlittle(data, length, 0u);
}

#endif // XLB_ENABLE_XPT
