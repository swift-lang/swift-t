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
 * Functions used to write ADLB data checkpoint files.
 */

#ifdef XLB_ENABLE_XPT
#ifndef __XLB_XPT_H
#define __XLB_XPT_H

#include <stdio.h>

#include "adlb-defs.h"

/* 4MB blocks.  TODO: not hardcoded */
#define XLB_XPT_BLOCK_SIZE (4 * 1024 * 1024)

/* State for checkpoint file being written */
typedef struct {
  FILE *file;
  int curr_block;
} xlb_xpt_state;

/* Metadata for reading back checkpoint file */
typedef struct {
  FILE *file;
  int block_size; // Block size
  int ranks;      // Number of ranks
} xlb_xpt_read_state;

/* Setup checkpoint file.  This function should be called by all ranks,
   whether they intend to log checkpoint data or not.  This function will
   seek to first block in file for this rank.  It will also write any
   header info.  This must be called after xlb is initialized */
adlb_code xlb_xpt_init(const char *filename, xlb_xpt_state *state);

/* Open existing checkpoint file */
adlb_code xlb_xpt_open_read(const char *filename, xlb_xpt_read_state *state);

/* Move to next checkpoint block for this rank */
adlb_code xlb_xpt_next_block(xlb_xpt_state *state);

/* Write a checkpoint record */
adlb_code xlb_xpt_write(const void *key, int key_len, const void *val,
                        int val_len);

#endif // __XLB_XPT_H
#endif // XLB_ENABLE_XPT
