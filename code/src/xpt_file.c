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

static inline adlb_code block_start_seek(const char *xpt_filename,
                                        xlb_xpt_state *state);

adlb_code xlb_xpt_init(const char *xpt_filename, xlb_xpt_state *state)
{
  assert(xpt_filename != NULL);
  assert(state != NULL);
  state->file = fopen(xpt_filename, "w");
  CHECK_MSG(state->file != NULL, "Error opening file %s for write",
            xpt_filename);

  state->curr_block = xlb_comm_rank;
  adlb_code rc = block_start_seek(xpt_filename, state);
  ADLB_CHECK(rc);
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
   xpt_filename can be provided for error messages
 */
static inline adlb_code block_start_seek(const char *xpt_filename,
                                        xlb_xpt_state *state)
{
  int block_start = state->curr_block * XLB_XPT_BLOCK_SIZE;
  int rc = fseek(state->file, block_start, SEEK_CUR);
  if (xpt_filename != NULL) {
    CHECK_MSG(rc == 0, "Error seeking in checkpoint file %s", xpt_filename);
  } else {
    CHECK_MSG(rc == 0, "Error seeking in checkpoint file");
  }
  return ADLB_SUCCESS;
}
