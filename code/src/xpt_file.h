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
#ifndef __XLB_XPT_FILE_H
#define __XLB_XPT_FILE_H

#include <stdio.h>

#include "adlb-defs.h"
#include "adlb_types.h"

/* 4MB blocks.  TODO: not hardcoded */
#define XLB_XPT_BLOCK_SIZE (4 * 1024 * 1024)

/* State for checkpoint file being written */
typedef struct {
  FILE *file;
  uint32_t curr_block; // Number of current block
  bool empty_block; // Whether current block is empty
} xlb_xpt_state;

/* Metadata for reading back checkpoint file */
typedef struct {
  FILE *file;
  uint32_t block_size; // Block size
  uint32_t ranks;      // Number of ranks
  uint32_t curr_rank;  // Log from current rank being read
  
  uint32_t curr_block;
  bool started_block; // Whether we started reading the block
} xlb_xpt_read_state;

/* Setup checkpoint file.  This function should be called by all ranks,
   whether they intend to log checkpoint data or not.  This function will
   seek to first block in file for this rank.  It will also write any
   header info.  This must be called after xlb is initialized */
adlb_code xlb_xpt_init(const char *filename, xlb_xpt_state *state);

/* Close checkpoint file */
adlb_code xlb_xpt_close(xlb_xpt_state *state);

/* Move to next checkpoint block for this rank */
adlb_code xlb_xpt_next_block(xlb_xpt_state *state);

/* Write a checkpoint record.
  val_offset: offset of value record in file. */
adlb_code xlb_xpt_write(const void *key, int key_len, const void *val,
                int val_len, xlb_xpt_state *state, off_t *val_offset);

/* Read a checkpoint value at a value offset returned by xlb_xpt_write.
   buffer must be at least val_len in size */
adlb_code xlb_xpt_read_val(off_t val_offset, int val_len,
                           xlb_xpt_state *state, void *buffer);

/* Flush checkpoint writes */
adlb_code xlb_xpt_flush(xlb_xpt_state *state);

/* Open existing checkpoint file.  Defaults to reading checkpoints
   from rank 0. This can be changed with a call to xlb_xpt_read_select */
adlb_code xlb_xpt_open_read(const char *filename, xlb_xpt_read_state *state);

/* Close checkpoint read file */
adlb_code xlb_xpt_close_read(xlb_xpt_read_state *state);

/* Start reading the checkpoint stream of the specified rank. Called after
  the checkpoint file has been opened */
adlb_code xlb_xpt_read_select(xlb_xpt_read_state *state, uint32_t rank);

/* Read a checkpoint entry.

  Returns ADLB_NOTHING if no more valid records for this rank
  TODO: way to indicate corrupted record?

  buffer: caller-provided buffer used to store data
   TODO: way to tell caller how much buffer they need?
  key_len, val_len: length in bytes
  key, val: pointers into buffer for start of key/value data
 */
adlb_code xlb_xpt_read(xlb_xpt_read_state *state, adlb_buffer *buffer,
                       int *key_len, void **key, int *val_len, void **val);

#endif // __XLB_XPT_FILE_H
#endif // XLB_ENABLE_XPT
