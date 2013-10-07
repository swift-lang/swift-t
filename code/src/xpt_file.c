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
#include "xpt_file.h"

#include "checks.h"
#include "common.h"

// Magic number to put at start of blocks;
static const unsigned char xpt_magic_num = 0x42;

static inline adlb_code block_start_seek(const char *filename,
                                         xlb_xpt_state *state);
static inline bool is_xpt_leader(void);
static inline adlb_code xpt_header_write(xlb_xpt_state *state);

#define FWRITE_CHECKED(data, size, count, state) {          \
  int count2 = (count);                                     \
  int fwrc = fwrite((data), (size), count2, (state)->file); \
  CHECK_MSG(fwrc == count2, "Error writing checkpoint");    \
}

#define FREAD_CHECKED(data, size, count, state) {         \
  int count2 = (count);                                   \
  int frrc = fread((data), (size), count2, (state)->file);\
  CHECK_MSG(frrc == count2, "Error reading checkpoint");  \
}

#define FWRITE_CHECKED_INT(val, state) {                  \
  int val2 = val;                                         \
  FWRITE_CHECKED(&(val2), sizeof(val2), 1, state);        \
}

#define FREAD_CHECKED_INT(data, state) {                  \
  FREAD_CHECKED(&(data), sizeof(int), 1, state);          \
}

adlb_code xlb_xpt_init(const char *filename, xlb_xpt_state *state)
{
  assert(filename != NULL);
  assert(state != NULL);
  state->file = fopen(filename, "w");
  CHECK_MSG(state->file != NULL, "Error opening file %s for write",
            filename);

  state->curr_block = xlb_comm_rank;
  adlb_code rc = block_start_seek(filename, state);
  ADLB_CHECK(rc);

  // TODO: support other ranks being "leader"
  if (is_xpt_leader())
  {
    rc = xpt_header_write(state);
    ADLB_CHECK(rc);
  }

  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_next_block(xlb_xpt_state *state)
{
  // Round-robin block allocation for now
  state->curr_block += xlb_comm_rank;
  adlb_code rc = block_start_seek(NULL, state);
  ADLB_CHECK(rc);
  return ADLB_SUCCESS;
}

/*
   Seek to start of block.
   filename can be provided for error messages
 */
static inline adlb_code block_start_seek(const char *filename,
                                        xlb_xpt_state *state)
{
  int block_start = state->curr_block * XLB_XPT_BLOCK_SIZE;
  int rc = fseek(state->file, block_start, SEEK_CUR);
  if (filename != NULL) {
    CHECK_MSG(rc == 0, "Error seeking in checkpoint file %s", filename);
  } else {
    CHECK_MSG(rc == 0, "Error seeking in checkpoint file");
  }
  return ADLB_SUCCESS;
}

static inline bool is_xpt_leader(void)
{
  // For now, assume rank 0 is the leader
  // TODO: more flexibility e.g. if rank 0 doesn't want to checkpoint
  return (xlb_comm_rank == 0);
}

static inline adlb_code xpt_header_write(xlb_xpt_state *state)
{
  int rc;
  
  rc = fputc(xpt_magic_num, state->file);
  CHECK_MSG(rc == xpt_magic_num, "Error writing checkpoint header");
  
  // Write info about structure of checkpoint file
  FWRITE_CHECKED_INT(XLB_XPT_BLOCK_SIZE, state);
  FWRITE_CHECKED_INT(xlb_comm_size, state);
  // TODO: more fields
  // TODO: checksum header
  // TODO: what if header overflows first block?
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_open_read(const char *filename, xlb_xpt_read_state *state)
{
  state->file = fopen(filename, "r");
  CHECK_MSG(state->file != NULL, "Could not open %s for read", filename);
  int magic_num = fgetc(state->file);
  CHECK_MSG(magic_num != xpt_magic_num, "Invalid magic number %i"
        " at start of checkpoint file %s: may be corrupted or not"
        " checkpoint", magic_num, filename);
  // TODO: verify checksum?
  FREAD_CHECKED_INT(state->block_size, state);
  FREAD_CHECKED_INT(state->ranks, state);
  return ADLB_SUCCESS;
}
