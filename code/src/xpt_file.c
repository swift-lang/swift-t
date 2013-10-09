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

#ifdef XLB_ENABLE_XPT

#include <limits.h>
#include <stdbool.h>
#include <stdint.h>

#include <zlib.h>

#include "checks.h"
#include "common.h"
#include "vint.h"

// Magic number to put at start of blocks;
static const unsigned char xpt_magic_num = 0x42;

// Functions to select blocks for a rank
static inline uint32_t first_block(uint32_t rank, uint32_t ranks);
static inline uint32_t next_block(uint32_t rank, uint32_t ranks,
                                  uint32_t curr);

static inline adlb_code block_start_seek(const char *filename,
                                         xlb_xpt_state *state);
static inline adlb_code block_init(xlb_xpt_state *state);
static inline adlb_code block_read_init(xlb_xpt_read_state *state,
                                        bool *empty);
static inline bool is_xpt_leader(void);
static inline adlb_code xpt_header_write(xlb_xpt_state *state);
static inline bool check_crc(xlb_xpt_read_state *state, int rec_len,
                             uint32_t crc, adlb_buffer *buffer);

#define FWRITE_CHECKED(data, size, count, state) {             \
  size_t count2 = (size_t)(count);                             \
  size_t fwrc = fwrite((data), (size), count2, (state)->file); \
  CHECK_MSG(fwrc == count2, "Error writing checkpoint");       \
}

#define FREAD_CHECKED(data, size, count, state) {             \
  size_t count2 = (count);                                    \
  size_t frrc = fread((data), (size), count2, (state)->file); \
  CHECK_MSG(frrc == count2, "Error reading checkpoint");      \
}

// TODO: endianness
#define FWRITE_CHECKED_UINT32(val, state) {                 \
  uint32_t val2 = val;                                      \
  FWRITE_CHECKED(&(val2), sizeof(val2), (size_t)1, state);  \
}

#define FREAD_CHECKED_UINT32(data, state) {                 \
  FREAD_CHECKED(&(data), sizeof(uint32_t), 1, state);       \
}

// Check fread return code, returning ADLB_DONE upon eof
#define CHECK_READ_EOF(code) {                              \
  if (code != 1)                                            \
  {                                                         \
    if (feof(state->file))                                  \
    {                                                       \
      return ADLB_DONE;                                     \
    }                                                       \
    else                                                    \
    {                                                       \
      printf("Error reading from checkpoint file: %i",      \
            ferror(state->file));                           \
      clearerr(state->file);                                \
      return ADLB_ERROR;                                    \
    }                                                       \
  }                                                         \
}

adlb_code xlb_xpt_init(const char *filename, xlb_xpt_state *state)
{
  assert(filename != NULL);
  assert(state != NULL);
  state->file = fopen(filename, "wb+");
  CHECK_MSG(state->file != NULL, "Error opening file %s for write",
            filename);

  state->curr_block = first_block((uint32_t)xlb_comm_rank,
                                  (uint32_t)xlb_comm_size);
  state->empty_block = true;
  adlb_code rc = block_start_seek(filename, state);
  ADLB_CHECK(rc);

  // TODO: support other ranks being "leader"
  if (is_xpt_leader())
  {
    rc = xpt_header_write(state);
    ADLB_CHECK(rc);
    state->empty_block = false;
  }

  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_close(xlb_xpt_state *state)
{
  assert(state->file != NULL);
  int rc = fclose(state->file);
  state->file = NULL;
  
  CHECK_MSG(rc == 0, "Error closing checkpoint file");
  return ADLB_SUCCESS;
}

static inline uint32_t first_block(uint32_t rank, uint32_t ranks)
{
  return rank;
}

static inline uint32_t next_block(uint32_t rank, uint32_t ranks,
                                  uint32_t curr)
{
  return curr + ranks;
}

adlb_code xlb_xpt_next_block(xlb_xpt_state *state)
{
  assert(state->file != NULL);
  // Round-robin block allocation for now
  state->curr_block = next_block((uint32_t)xlb_comm_rank,
             (uint32_t)xlb_comm_size, state->curr_block);
  adlb_code rc = block_start_seek(NULL, state);
  ADLB_CHECK(rc);
  state->empty_block = true;
  return ADLB_SUCCESS;
}

/*
   Seek to start of block.
   filename can be provided for error messages
 */
static inline adlb_code block_start_seek(const char *filename,
                                        xlb_xpt_state *state)
{
  assert(state->file != NULL);
  off_t block_start = ((off_t)state->curr_block) * XLB_XPT_BLOCK_SIZE;
  int rc = fseek(state->file, block_start, SEEK_SET);
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
  assert(state->file != NULL);
  adlb_code rc = block_init(state);
  ADLB_CHECK(rc);
  // Write info about structure of checkpoint file
  FWRITE_CHECKED_UINT32(XLB_XPT_BLOCK_SIZE, state);
  FWRITE_CHECKED_UINT32((uint32_t)xlb_comm_size, state);
  // TODO: more fields
  // TODO: checksum header
  // TODO: what if header overflows first block?

  // Make sure header gets written out
  int c = fflush(state->file);
  CHECK_MSG(c != EOF, "Error flushing header");
  return ADLB_SUCCESS;
}

/*
   Initialize a new empty block by writing magic number to first
   byte and setting state indicator.
 */
static inline adlb_code block_init(xlb_xpt_state *state)
{
  assert(state->file != NULL);
  assert(state->empty_block);
  int rc;
  rc = fputc(xpt_magic_num, state->file);
  CHECK_MSG(rc == xpt_magic_num, "Error writing checkpoint header");
  state->empty_block = false;
  return ADLB_SUCCESS;
}

/*
  Write a checkpoint log entry in this format:
  +------------+-------------------------------------+
  | checksum   | <crc32 of rest of record>           |
  +------------+-------------------------------------+
  | record_len | <vint-encoded length of rest>       |
  +------------+-------------------------------------+
  | key_len     |<vint-encoded key length in bytes>  |
  +------------+-------------------------------------+
  | key_data:  | <binary data>                       |
  +------------+-------------------------------------+
  | value_data | <binary data>                       |
  +------------+-------------------------------------+

  Advances to next block if necessary.

  Checkpoint entries can be split across blocks.  In this
  case we will make sure that the checksum and record length
  are in the first block, but the remainder can be split
  across blocks.
 */
adlb_code xlb_xpt_write(const void *key, int key_len, const void *val,
                int val_len, xlb_xpt_state *state, off_t *val_offset)
{
  assert(state->file != NULL);
  assert(key_len >= 0);
  assert(val_len >= 0);
  
  adlb_code rc;

  // Buffers for encoded vint values
  Byte key_len_enc[VINT_MAX_BYTES];
  Byte rec_len_enc[VINT_MAX_BYTES];
  // Length in bytes of encoded vint values
  uInt key_len_encb, rec_len_encb;

  // encode key_len using variable-length int format
  key_len_encb = (uInt)vint_encode((int64_t)key_len, key_len_enc);

  // Record length w/o CRC or record length
  int64_t rec_len = (int64_t)key_len_encb + key_len + val_len;
  rec_len_encb = (uInt)vint_encode(rec_len, rec_len_enc);

  // Total record length with 32-bit CRC and record length
  int64_t rec_total_len = rec_len + rec_len_encb + 4;

  // Calculate CRC from components
  uLong crc = crc32(0L, Z_NULL, 0);
  crc = crc32(crc, rec_len_enc, rec_len_encb);
  crc = crc32(crc, key_len_enc, key_len_encb);
  crc = crc32(crc, key, (uInt)key_len);
  crc = crc32(crc, val, (uInt)val_len);

  // TODO: split across blocks for records larger than single block.
  // For smaller records want to align to block boundary to make it
  // more possible to read partially corrupted checkpoint files.
  CHECK_MSG(rec_total_len <= XLB_XPT_BLOCK_SIZE - 1,
            "Record too large for checkpoint block: %"PRId64" v. %i",
            rec_total_len, XLB_XPT_BLOCK_SIZE);

  if (!state->empty_block)
  {
    // check for advance to new block
    off_t block_start = ((off_t)state->curr_block) * XLB_XPT_BLOCK_SIZE;
    off_t curr_pos = ftello(state->file);
    
    assert(curr_pos > block_start &&
           curr_pos <= block_start + XLB_XPT_BLOCK_SIZE);
    off_t block_remaining = XLB_XPT_BLOCK_SIZE - (curr_pos - block_start);
    if (rec_total_len >= block_remaining)
    {
      rc = xlb_xpt_next_block(state);
      ADLB_CHECK(rc);
    }
  }

  // Write magic number at start of all blocks
  if (state->empty_block)
  {
    rc = block_init(state);
    ADLB_CHECK(rc);
  }
  
  // Write out all data in sequence
  FWRITE_CHECKED_UINT32((uint32_t)crc, state);
  FWRITE_CHECKED(rec_len_enc, 1, rec_len_encb, state);
  FWRITE_CHECKED(key_len_enc, 1, key_len_encb, state);
  FWRITE_CHECKED(key, 1, key_len, state);
  if (val_offset != NULL)
  {
    // Return offset of value in file if needed
    *val_offset = ftello(state->file);
    CHECK_MSG(*val_offset >= 0, "Error getting file value offset");
  }
  FWRITE_CHECKED(val, 1, val_len, state);
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_read_val(off_t val_offset, int val_len,
                           xlb_xpt_state *state, void *buffer)
{
  assert(state->file != NULL);
  assert(val_len >= 0);

  int rc;
  fpos_t pos;
  // Use fgetpos/fsetpos to return file pointer to old position
  rc = fgetpos(state->file, &pos);
  CHECK_MSG(rc == 0, "Error using fgetpos on checkpoint file");

  rc = fseeko(state->file, val_offset, SEEK_SET);
  CHECK_MSG(rc == 0, "Error using fseeko on checkpoint file");

  size_t wrote = fread(buffer, 1, (size_t)val_len, state->file);

  // Reset file position before checking if read was ok
  rc = fsetpos(state->file, &pos);
  CHECK_MSG(rc == 0, "Error using fsetpos on checkpoint file");
  
  CHECK_MSG(wrote == (size_t)val_len, "Error reading value from "
                                      "checkpoint file");

  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_flush(xlb_xpt_state *state)
{
  assert(state->file != NULL);
  int rc = fflush(state->file);
  CHECK_MSG(rc != EOF, "Error flushing checkpoint file");

  return ADLB_SUCCESS;
}

/*
  Open a checkpoint file for reading.
 */
adlb_code xlb_xpt_open_read(const char *filename, xlb_xpt_read_state *state)
{
  state->file = fopen(filename, "rb");
  CHECK_MSG(state->file != NULL, "Could not open %s for read", filename);

  state->curr_rank = 0;
  state->curr_block = first_block(state->curr_rank, state->ranks);

  int magic_num = fgetc(state->file);
  CHECK_MSG(magic_num != xpt_magic_num, "Invalid magic number %i"
        " at start of checkpoint file %s: may be corrupted or not"
        " checkpoint", magic_num, filename);

  // TODO: verify header checksum?
  FREAD_CHECKED_UINT32(state->block_size, state);
  FREAD_CHECKED_UINT32(state->ranks, state);
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_close_read(xlb_xpt_read_state *state)
{
  assert(state->file != NULL);
  int rc = fclose(state->file);
  state->file = NULL;
  CHECK_MSG(rc != 0, "Error closing checkpoint file");
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_read_select(xlb_xpt_read_state *state, uint32_t rank)
{
  assert(state->file != NULL);
  CHECK_MSG(rank >= 0 && rank < state->ranks, "Invalid rank: %"PRId32, rank);
  state->curr_rank = rank;
  state->curr_block = first_block(state->curr_rank, state->ranks);

  off_t block_start = ((off_t)state->curr_block) * XLB_XPT_BLOCK_SIZE;

  int rc = fseek(state->file, block_start, SEEK_SET);
  CHECK_MSG(rc == 0, "Error seeking in checkpoint file");

  // Wait until later to init block
  state->started_block = false; 
  return ADLB_SUCCESS;
}

/*
   Start reading a new block.  Check for magic number. Return error
   upon corruption.  Set empty to true if block has no contents.
   This can be because we hit the end of the file, or because we
   we got a null byte at start of block, indicating (probably) that
   the block is empty in a sparse file.
   Called after we seeked to start of block.
 */
static inline adlb_code block_read_init(xlb_xpt_read_state *state,
                                        bool *empty)
{
  assert(state->file != NULL);
  int magic_num = fgetc(state->file);
  if (magic_num == EOF || magic_num == 0) {
    *empty = true;
    return ADLB_SUCCESS;
  }
  CHECK_MSG(magic_num != xpt_magic_num, "Invalid magic number %i"
        " at start of checkpoint block: may be corrupted", magic_num);
  *empty = false;
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_read(xlb_xpt_read_state *state, adlb_buffer *buffer,
   int *key_len, void **key, int *val_len, void **val, off_t *val_offset)
{
  size_t frrc;
  assert(state->file != NULL);
  assert(buffer->data != NULL);

  // TODO: advance to next block if necessary
  // TODO: how to detect if no more data in block?

  uint32_t crc;
  // Buffers for encoded vint values
  Byte rec_len_enc[VINT_MAX_BYTES];
  // Length in bytes of encoded vint values
  int rec_len_encb;
  int64_t rec_len64, key_len64;

  // Get crc
  frrc = fread(&crc, sizeof(crc), 1, state->file);
  CHECK_READ_EOF(frrc);

  off_t rec_offset = ftello(state->file);
  CHECK_MSG(rec_offset == 0, "Error using ftello");

  // get record length from file reading byte-by-byte
  rec_len_encb = vint_file_decode(state->file, &rec_len64);
  if (rec_len_encb < 0)
  {
    ERR_PRINTF("Could not decode record length from file\n"); 
    return ADLB_NOTHING;
  }

  // sanity check for record length
  if(rec_len64 < 0 || rec_len64 > INT_MAX)
  {
    ERR_PRINTF("Out of range record length: %"PRId64"\n", rec_len64);
    return ADLB_NOTHING;
  }
  
  // buffer too small: signal caller
  if(buffer->length < rec_len64)
  {
    // consider case where record length is corrupted: check CRC by
    // reading directly from file to avoid danger of allocating
    // too-big buffer.
    if (!check_crc(state, (int)rec_len64, crc, buffer))
    {
      if (feof(state->file))
      {
        return ADLB_DONE;
      }
      // Bad record
      return ADLB_NOTHING;
    }
    // reset position to start of record
    fseeko(state->file, rec_offset - (off_t)sizeof(crc), SEEK_SET);
    *key_len = (int)rec_len64;
    return ADLB_RETRY;
  }

  // Load rest of record into caller buffer
  // TODO: handle if rest of record split across blocks
  frrc = fread(buffer->data, 1, (size_t)rec_len64, state->file);
  CHECK_READ_EOF(frrc);

  // Now we can check crc 
  uLong crc_calc = crc32(0L, Z_NULL, 0);
  crc_calc = crc32(crc_calc, rec_len_enc, (uInt)rec_len_encb);
  crc_calc = crc32(crc_calc, (Byte*)buffer->data, (uInt)rec_len64);
  if (crc_calc != crc)
  {
    // Invalid or corrupted record
    return ADLB_NOTHING;
  }

  // CRC check passed: checkpoint record is probably intact
  int key_len_encb = vint_decode(buffer->data, (int)rec_len64, &key_len64);
  if (key_len_encb < 0)
  {
    ERR_PRINTF("Error decoding vint for key length\n");
    return ADLB_NOTHING;
  }
  if (key_len64 < 0 || key_len64 > INT_MAX)
  {
    ERR_PRINTF("Out of range key length: %"PRId64"\n", key_len64);
    return ADLB_NOTHING;
  }
  if (key_len64 > rec_len64 - key_len_encb)
  {
    ERR_PRINTF("Key length too long for record: %"PRId64" v. %"PRId64,
                key_len64, rec_len64);
    return ADLB_NOTHING;
  }

  *key_len = (int)key_len64;
  *val_len = (int)(rec_len64 - (int64_t)key_len_encb - key_len64);

  // Work out relative offsets of key/value data from record start
  int key_rel = key_len_encb;
  int val_rel = key_rel + *key_len;
  *key = buffer->data + key_rel;
  *val = buffer->data + val_rel;
  *val_offset = rec_offset + (off_t)val_rel;
  
  return ADLB_SUCCESS;
}

/*
  Check crc by reading from file

  buffer: buffer to use
  crc: crc to compare against
  state: file to read from.  Read position will be moved by rec_len
        (or to eof)
  returns true if it matches, false if error encountered
 */
static inline bool check_crc(xlb_xpt_read_state *state, int rec_len,
                    uint32_t crc, adlb_buffer *buffer)
{
  int read = 0;
  uLong crc_calc = crc32(0L, Z_NULL, 0);
  while (read < rec_len)
  {
    // TODO: handle split across blocks
    int remaining = rec_len - read;
    size_t to_read = (size_t)(buffer->length < remaining ?
                              buffer->length : remaining);
    size_t frrc = fread(buffer->data, 1U, to_read, state->file);
    if (frrc != to_read)
    {
      return false;
    }
    crc_calc = crc32(crc_calc, (Byte*)buffer->data, (uInt)to_read);
  }
  return (crc_calc == crc);
}

#endif // XLB_ENABLE_XPT
