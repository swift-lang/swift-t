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

// Needed for pread() on BlueWaters:
#define _XOPEN_SOURCE 500
#include "xpt_file.h"

#ifdef XLB_ENABLE_XPT

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include <zlib.h>

#include "checks.h"
#include "common.h"
#include "debug.h"
#include "vint.h"

// Magic number to put at start of blocks;
static const unsigned char xpt_magic_num = 0x42;

// Sync marker to put at start of records
static const uint32_t xpt_sync_marker = 0x5F1C0B73;

/*
 *Length of end-of-file marker record:
 * Sync marker, CRC marker, zero record length
 */
#define EOF_REC_BYTES (2*sizeof(uint32_t) + (uint32_t)vint_bytes(0))

typedef struct
{
  xpt_block_num_t block;
  xpt_block_pos_t block_pos;
} xpt_file_pos;

static bool is_init(xlb_xpt_state *state);

// Functions to select blocks for a rank
static xpt_block_num_t first_block(xpt_rank_t rank, xpt_rank_t ranks);
static xpt_block_num_t
next_block(xpt_rank_t ranks, xpt_block_num_t curr);

static adlb_code block_move_next(xlb_xpt_state *state);
static adlb_code block_move(xpt_block_num_t block,
            const char *filename, xlb_xpt_state *state);

// Primitives for reading and writing to blocked files
static adlb_code bufwrite(xlb_xpt_state *state,
                  const void *data, xpt_block_pos_t length);
static adlb_code bufwrite_uint32(xlb_xpt_state *state,
                                     uint32_t val);
static adlb_code checked_fread(xlb_xpt_read_state *state, void *buf,
                                      xpt_block_pos_t length);
static adlb_code blkgetc(xlb_xpt_read_state *state, unsigned char *c);
static adlb_code blkread(xlb_xpt_read_state *state, void *buf,
                                 xpt_block_pos_t length);
static uint32_t parse_uint32(unsigned char buf[4]);
static adlb_code checked_fread_uint32(xlb_xpt_read_state *state,
                                             uint32_t *data);
static adlb_code blkread_uint32(xlb_xpt_read_state *state,
                                       uint32_t *data);
static adlb_code blkread_vint(xlb_xpt_read_state *state,
                int64_t *data, unsigned char *encoded, int *consumed);


static xpt_file_pos file_pos_add(xpt_file_pos pos, xpt_file_pos_t add);
static xpt_file_pos xpt_get_file_pos(xlb_xpt_state *state,
                                    bool after_buffered);
static xpt_file_pos_t xpt_file_offset(xlb_xpt_state *state,
                                    bool after_buffered);
static xpt_file_pos xpt_read_pos(const xlb_xpt_read_state *state);
static xpt_file_pos_t xpt_read_offset(const xlb_xpt_read_state *state);
static adlb_code
seek_file_pos(xlb_xpt_read_state *state, xpt_file_pos pos);
static adlb_code flush_buffers(xlb_xpt_state *state);

static bool is_xpt_leader(void);
static adlb_code xpt_header_read(xlb_xpt_read_state *state,
                                        const char *filename);
static adlb_code xpt_header_write(xlb_xpt_state *state);
static adlb_code write_entry(xlb_xpt_state *state,
    size_t rec_len, const void *key, size_t key_len,
    const void *key_len_enc, size_t key_len_encb,
    const void *val, size_t val_len, xpt_file_pos_t *val_offset);
static bool check_crc(xlb_xpt_read_state *state, int rec_len,
                             uint32_t crc, adlb_buffer *buffer);
static void xpt_read_resync(xlb_xpt_read_state *state,
                            xpt_file_pos resync_point);
static adlb_code
seek_read(xlb_xpt_read_state *state, xpt_file_pos_t offset);
static adlb_code block_read_advance(xlb_xpt_read_state *state);
static adlb_code block_read_move(xlb_xpt_read_state *state,
                        xpt_block_num_t new_block);

adlb_code xlb_xpt_write_init(const char *filename, xlb_xpt_state *state)
{
  assert(filename != NULL);
  assert(state != NULL);
  // Open file for reading and writing
  // TODO: if file already exists from previous one, this won't truncate
  state->fd = open(filename, O_RDWR | O_CREAT, S_IRWXU);
  CHECK_MSG(state->fd != -1, "Error opening file %s for write. "
        "Error code %i. %s", filename, errno, strerror(errno));

  state->buffer = malloc(XLB_XPT_BUFFER_SIZE);
  CHECK_MSG(state->buffer != NULL, "Error allocating buffer");
  state->buffer_used = 0;

  xpt_block_num_t block = first_block((xpt_rank_t)xlb_comm_rank,
                                      (xpt_rank_t)xlb_comm_size);
  adlb_code rc = block_move(block, filename, state);
  ADLB_CHECK(rc);

  if (is_xpt_leader())
  {
    rc = xpt_header_write(state);
    ADLB_CHECK(rc);
  }

  return ADLB_SUCCESS;
}

static bool is_init(xlb_xpt_state *state)
{
  return state->fd >= 0;
}

adlb_code xlb_xpt_write_close(xlb_xpt_state *state)
{
  assert(is_init(state));
  /*
   * Need to mark end of file.  Several cases must be considered to
   * allow reader to correctly distinguish end of this rank's checkpoints
   * vs. file corruption.
   * - If we are at the start of an empty block -> do nothing, block is
   *   empty
   * - If we are in middle of block -> write special zero-length record
   * - If we are at the end of a block, with not enough space for
   *    the zero length record -> do nothing.  Don't start new block
   */
  xpt_file_pos pos = xpt_get_file_pos(state, true);
  assert(pos.block_pos <= XLB_XPT_BLOCK_SIZE);
  if (pos.block_pos > 0 &&
    (XLB_XPT_BLOCK_SIZE - pos.block_pos) >= EOF_REC_BYTES)
  {
    // Write zero length record as marker
    write_entry(state, 0, NULL, 0, NULL, 0, NULL, 0, NULL);
  }

  adlb_code code = xlb_xpt_flush(state);
  ADLB_CHECK(code);

  int rc = close(state->fd);
  state->fd = -1;

  free(state->buffer);
  state->buffer = NULL;

  CHECK_MSG(rc == 0, "Error closing checkpoint file: Error code %i %s",
              errno, strerror(errno));
  return ADLB_SUCCESS;
}

static xpt_block_num_t first_block(xpt_rank_t rank, xpt_rank_t ranks)
{
  return rank;
}

static xpt_block_num_t next_block(xpt_rank_t ranks, xpt_block_num_t curr)
{
  return curr + ranks;
}

/*
  Flush internal buffers
 */
static adlb_code flush_buffers(xlb_xpt_state *state)
{
  assert(is_init(state));
  assert(state->buffer_used <= XLB_XPT_BUFFER_SIZE);
  assert(state->curr_block_pos < XLB_XPT_BLOCK_SIZE);

  DEBUG("Flushing buffers: %"PRIu32" bytes at file offset %zi",
            state->buffer_used, xpt_file_offset(state, false));

  const unsigned char *buf_pos = state->buffer;
  xpt_block_pos_t buf_left = state->buffer_used;
  while (buf_left > 0)
  {
    xpt_file_pos_t curr_pos = xpt_file_offset(state, false);
    xpt_block_pos_t block_left = XLB_XPT_BLOCK_SIZE -
                                 state->curr_block_pos;
    xpt_block_pos_t write_size = block_left < buf_left ?
                                 block_left : buf_left;

    if (write_size > 0)
    {
      TRACE("pwrite %ui bytes @ %zi", write_size, curr_pos);
      ssize_t pwc = pwrite(state->fd, buf_pos, write_size, curr_pos);
      CHECK_MSG(pwc == write_size, "Error writing to checkpoint file at "
              "offset %zi", curr_pos);

      buf_pos += write_size;
      buf_left -= write_size;
    }

    if (write_size == block_left)
    {
      // Hit end of block, advance to next block
      adlb_code ac = block_move_next(state);
      ADLB_CHECK(ac);
    }
    else
    {
      // Within same block
      state->curr_block_pos += write_size;
      assert(state->curr_block_pos < XLB_XPT_BLOCK_SIZE);
    }
  }
  state->buffer_used = 0;
  return ADLB_SUCCESS;
}

/*
  Move to next block
 */
static adlb_code block_move_next(xlb_xpt_state *state)
{
  // Round-robin block allocation for now
  xpt_block_num_t block = next_block((xpt_rank_t)xlb_comm_size,
                                     state->curr_block);
  return block_move(block, NULL, state);
}

/*
   Move to start of block.
   filename can be provided for error messages
 */
static adlb_code block_move(xpt_block_num_t block,
        const char *filename, xlb_xpt_state *state)
{
  assert(is_init(state));

  DEBUG("Rank %"PRIu32" moving to start of block %"PRIu32,
        xlb_comm_rank, block);
  state->curr_block = block;
  state->curr_block_start = ((xpt_file_pos_t)block) * XLB_XPT_BLOCK_SIZE;
  state->curr_block_pos = 0;
  return ADLB_SUCCESS;
}

static bool is_xpt_leader(void)
{
  // For now, assume rank 0 is the leader
  // TODO: more flexibility e.g. if rank 0 doesn't want to checkpoint
  return (xlb_comm_rank == 0);
}

static adlb_code xpt_header_write(xlb_xpt_state *state)
{
  assert(is_init(state));
  assert(state->curr_block == 0);
  assert(state->curr_block_pos == 0);
  assert(state->buffer_used == 0);

  adlb_code rc;

  // Write info about structure of checkpoint file
  rc = bufwrite_uint32(state, XLB_XPT_BLOCK_SIZE);
  ADLB_CHECK(rc);
  rc = bufwrite_uint32(state, (xpt_rank_t)xlb_comm_size);
  ADLB_CHECK(rc);
  // TODO: more fields
  // TODO: checksum header

  // Make sure header gets written out
  rc = xlb_xpt_flush(state);
  CHECK_MSG(rc == ADLB_SUCCESS, "Error flushing header");
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

  Checkpoint entries can be split across blocks.
 */
adlb_code
xlb_xpt_write(const void *key, size_t key_len,
              const void *val, size_t val_len,
              xlb_xpt_state *state, xpt_file_pos_t *val_offset)
{
  DEBUG("Writing entry to checkpoint file key_len: %zu, val_len: %zu, "
        "Block: %"PRIu32, key_len, val_len, state->curr_block);
  assert(is_init(state));

  // Buffer for encoded vint
  Byte key_len_enc[VINT_MAX_BYTES];
  // Length in bytes of encoded vint
  size_t key_len_encb;

  // encode key_len using variable-length int format
  key_len_encb = (size_t) vint_encode((int64_t) key_len, key_len_enc);

  // Record length w/o CRC or record length
  size_t rec_len = key_len_encb + key_len + val_len;
  return write_entry(state, rec_len, key, key_len, key_len_enc,
      key_len_encb, val, val_len, val_offset);
}

/*
  Internal function to actually write entry to file.
  rec_len: if null, write "empty" entry as end of file marker
 */
static adlb_code
write_entry(xlb_xpt_state *state, size_t rec_len,
    const void *key, size_t key_len,
    const void *key_len_enc, size_t key_len_encb,
    const void *val, size_t val_len, xpt_file_pos_t *val_offset)
{
  // Buffer for encoded vint
  Byte rec_len_enc[VINT_MAX_BYTES];
  // Length in bytes of encoded vint
  uInt rec_len_encb;

  rec_len_encb = (uInt) vint_encode((int64_t) rec_len, rec_len_enc);

  bool empty_record = (rec_len == 0);

  // Calculate CRC from components
  uLong crc = crc32(0L, Z_NULL, 0);
  crc = crc32(crc, rec_len_enc, rec_len_encb);
  if (!empty_record)
  {
    crc = crc32(crc, key_len_enc, (uInt)key_len_encb);
    crc = crc32(crc, key, (uInt)key_len);
    crc = crc32(crc, val, (uInt)val_len);
  }

  TRACE("CRC: %lx", crc);

  DEBUG("Writing checkpoint entry at offset %zi",
        xpt_file_offset(state, true));

  adlb_code rc;
  // Write out all data in sequence
  // First write sync marker
  rc = bufwrite_uint32(state, xpt_sync_marker);
  ADLB_CHECK(rc);

  rc = bufwrite_uint32(state, (uint32_t)crc);
  ADLB_CHECK(rc);

  rc = bufwrite(state, rec_len_enc, rec_len_encb);
  ADLB_CHECK(rc);

  if (!empty_record)
  {
    rc = bufwrite(state, key_len_enc, (uInt)key_len_encb);
    ADLB_CHECK(rc);

    rc = bufwrite(state, key, (uInt)key_len);
    ADLB_CHECK(rc);

    if (val_offset != NULL)
    {
      // Return offset of value in file if needed
      *val_offset = xpt_file_offset(state, true);
      TRACE("val_offset=%zi", *val_offset);
    }

    rc = bufwrite(state, val, (uInt)val_len);
    ADLB_CHECK(rc);
  }

  return ADLB_SUCCESS;
}

adlb_code
xlb_xpt_read_val_w(xlb_xpt_state *state, xpt_file_pos_t val_offset,
                            size_t val_len, void *buffer)
{
  // TODO: it would be better to reread entire record to make sure
  //       we don't get a corrupted record.
  // checkpoint is in file currently being written.
  assert(is_init(state));

  xpt_block_num_t block = (xpt_block_num_t)
          (val_offset / XLB_XPT_BLOCK_SIZE);
  xpt_block_pos_t block_pos = (xpt_block_pos_t)
          (val_offset % XLB_XPT_BLOCK_SIZE);
  char *buf_pos = (char*)buffer;
  xpt_block_pos_t val_remaining = (xpt_block_pos_t)val_len;
  DEBUG("Reading val %"PRIu32" bytes @ offset %zi of current file",
        val_remaining, val_offset);
  while (val_remaining > 0)
  {
    xpt_file_pos_t read_offset = ((xpt_file_pos_t)block) *
                            XLB_XPT_BLOCK_SIZE + block_pos;
    xpt_block_pos_t block_left = XLB_XPT_BLOCK_SIZE - block_pos;
    xpt_block_pos_t to_read = block_left < val_remaining ?
                              block_left : val_remaining;

    DEBUG("Read val chunk: %"PRIu32" bytes @ %zi", to_read, read_offset);

    if (to_read > 0)
    {
      ssize_t read_b = pread(state->fd, buf_pos, to_read, read_offset);
      CHECK_MSG(read >= 0, "Error reading back checkpoint value: "
                "%d: %s", errno, strerror(errno));
      if (read_b < to_read)
      {
        ERR_PRINTF("Trying to read checkpoint value that is past end "
             "of file: %"PRIu32" bytes @ offset %zi, only could read "
             "%zi\n", to_read, read_offset, read_b);
        return ADLB_ERROR;
      }

      val_remaining -= to_read;
      buf_pos += to_read;
    }

    if (block_left == to_read)
    {
      // advance to next block
      block = next_block((xpt_rank_t)xlb_comm_size, block);
      DEBUG("Reading val: move to next block %"PRIu32, block);
      block_pos = 1; // Skip magic number
    }
  }

  return ADLB_SUCCESS;
}


adlb_code xlb_xpt_read_val_r(xlb_xpt_read_state *state,
                            xpt_file_pos_t val_offset,
                            size_t val_len, void *buffer)
{
  assert(state != NULL);
  // TODO: it would be better to reread entire record to make sure
  //       we don't get a corrupted record.
  adlb_code ac;
  DEBUG("Reading value: %zu bytes at offset %zi in file %s", val_len,
        val_offset, state->filename);

  ac = seek_read(state, val_offset);
  CHECK_MSG(ac == ADLB_SUCCESS, "Error seeking to %zi in file %s\n",
          val_offset, state->filename);

  ac = blkread(state, buffer, (xpt_block_pos_t)val_len);
  CHECK_MSG(ac == ADLB_SUCCESS, "Error reading %zu bytes at offset "
          "%zi in file %s", val_len,
          val_offset, state->filename);

  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_flush(xlb_xpt_state *state)
{
  assert(is_init(state));

  // Get rid of any buffer contents first
  adlb_code ac = flush_buffers(state);
  ADLB_CHECK(ac);

  // Then try to force sync to disk
  int rc = fsync(state->fd);
  CHECK_MSG(rc != EOF, "Error flushing checkpoint file");
  DEBUG("Finished flushing checkpoint file");

  return ADLB_SUCCESS;
}

/*
  Open a checkpoint file for reading.
 */
adlb_code
xlb_xlb_xpt_open_read(xlb_xpt_read_state *state, const char *filename)
{
  state->file = fopen(filename, "rb");
  CHECK_MSG(state->file != NULL, "Could not open %s for read", filename);

  state->curr_rank = 0;
  state->curr_block = 0;
  state->curr_block_pos = 0;
  state->end_of_stream = false;

  int magic_num = fgetc(state->file);
  CHECK_MSG(magic_num == xpt_magic_num, "Invalid magic number %i"
        " at start of checkpoint file %s: may be corrupted or not"
        " checkpoint", magic_num, filename);
  state->curr_block_pos++;

  adlb_code rc = xpt_header_read(state, filename);
  ADLB_CHECK(rc)

  DEBUG("Opened file %s block size %"PRIu32" ranks %"PRIu32, filename,
        state->block_size, state->ranks);
  state->filename = strdup(filename);
  DEBUG("Opened %p name: %s", state, filename);
  return ADLB_SUCCESS;
}

/*
  Read header from current position in file, assuming we're byte 2 of
  file (seek to start, then check magic number before calling this).
  read_hdr: if true, read values and check.  If false, just move past
            header
 */
static adlb_code
xpt_header_read(xlb_xpt_read_state *state, const char *filename)
{
  /*
   * Use fread instead of blkread since blkread assumes that
   * block_size, etc. parameters are initialized. THe header
   * should all be in first block of file.
   */
  adlb_code rc;
  rc = checked_fread_uint32(state, &state->block_size);
  CHECK_MSG(rc == ADLB_SUCCESS, "Error reading header");
  rc = checked_fread_uint32(state, &state->ranks);
  CHECK_MSG(rc == ADLB_SUCCESS, "Error reading header");

  CHECK_MSG(state->block_size > 0, "Block size cannot be zero in file "
            "%s", filename);
  CHECK_MSG(state->ranks > 0, "Ranks cannot be zero in file %s",
            filename);
  // TODO: header checksum?
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_close_read(xlb_xpt_read_state *state)
{
  assert(state->file != NULL);

  free(state->filename);
  state->filename = NULL;

  int rc = fclose(state->file);
  state->file = NULL;
  CHECK_MSG(rc == 0, "Error closing checkpoint file");
  return ADLB_SUCCESS;
}

adlb_code
xlb_xpt_read_select(xlb_xpt_read_state *state, xpt_rank_t rank)
{
  assert(state->file != NULL);
  DEBUG("Select rank %"PRIu32" for reading", rank);
  CHECK_MSG(rank < state->ranks, "Invalid rank: %"PRIu32, rank);
  state->curr_rank = rank;
  xpt_rank_t rank_block1 = first_block(state->curr_rank, state->ranks);
  adlb_code rc = block_read_move(state, rank_block1);
  if (rc == ADLB_DONE)
  {
    DEBUG("No entries for rank %"PRIu32, rank);
    return rc;
  }
  else if (rc != ADLB_SUCCESS)
  {
    ERR_PRINTF("Error moving to start of first block %"PRIu32
               " for rank %"PRIu32"\n", rank_block1, rank);
    return rc;
  }
  return ADLB_SUCCESS;
}

/*
   Start reading a new block.  Check for magic number. Return error
   upon corruption.  Set empty to true if block has no contents.
   This can be because we hit the end of the file, or because we
   we got a null byte at start of block, indicating (probably) that
   the block is empty in a sparse file.
   Called after we seeked to start of block.

   Return ADLB_DONE if we hit EOF
 */
static adlb_code block_read_advance(xlb_xpt_read_state *state)
{
  assert(state->file != NULL);
  xpt_block_num_t new_block = next_block(state->ranks,
                                         state->curr_block);
  return block_read_move(state, new_block);
}

/*
  Move to start of new block, checking magic number to make sure it is
  filled. Return ADLB_DONE if we hit end of checkpoints for current rank.
 */
static adlb_code
block_read_move(xlb_xpt_read_state *state, xpt_block_num_t new_block)
{
  DEBUG("Moving from block %"PRIu32" to block %"PRIu32" for rank "
        "%"PRIu32" (%"PRIu32" total)",
        state->curr_block, new_block, state->curr_rank, state->ranks);
  state->curr_block = new_block;
  state->curr_block_pos = 0;

  xpt_file_pos_t block_start = ((xpt_file_pos_t)state->curr_block) *
                               state->block_size;
  int rc = fseek(state->file, block_start, SEEK_SET);
  if (rc != 0)
  {
    if (feof(state->file))
    {
      state->end_of_stream = true;
      return ADLB_DONE;
    }
    else
    {
      ERR_PRINTF("Error seeking to offset %zi in checkpoint file\n",
                  block_start);
      state->end_of_stream = true;
      return ADLB_ERROR;
    }
  }

  int magic_num = fgetc(state->file);
  state->curr_block_pos++;
  if (magic_num == EOF || magic_num == 0) {
    DEBUG("Past last block in file %"PRIu32" for rank %"PRIu32"",
          state->curr_block, state->curr_rank);
    state->end_of_stream = true;
    return ADLB_DONE;
  }

  CHECK_MSG(magic_num == xpt_magic_num, "Invalid magic number %i"
        " at start of checkpoint block: may be corrupted", magic_num);

  state->end_of_stream = false; // Not at end of stream
  if (state->curr_block == 0)
  {
    // Move past file header
    adlb_code ac = xpt_header_read(state, NULL);
    ADLB_CHECK(ac);
  }
  return ADLB_SUCCESS;

}

/*
  Seek the read pointer to a particular point in the file
 */
static adlb_code
seek_read(xlb_xpt_read_state *state, xpt_file_pos_t offset)
{
  assert(state->file != NULL);
  int rc = fseek(state->file, offset, SEEK_SET);
  if (rc != 0)
  {
    if (feof(state->file))
    {
      ERR_PRINTF("Hit EOF seeking to offset %zi in checkpoint file\n",
                 offset);
      state->end_of_stream = true;
      return ADLB_DONE;
    }
    else
    {
      ERR_PRINTF("Error seeking to offset %zi in checkpoint file\n",
                 offset);
      state->end_of_stream = true;
      return ADLB_ERROR;
    }
  }
  state->curr_block = (xpt_block_num_t)(offset / state->block_size);
  state->curr_block_pos = (xpt_block_pos_t)(offset % state->block_size);
  state->end_of_stream = false;
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_read(xlb_xpt_read_state *state, adlb_buffer *buffer,
   size_t *key_len, void **key, size_t *val_len, void **val,
   xpt_file_pos_t *val_offset)
{
  adlb_code rc;
  assert(state->file != NULL);
  assert(buffer->data != NULL);

  uint32_t crc;
  uLong crc_calc;

  // Length in bytes of encoded vint values
  int rec_len_encb;
  int64_t rec_len64, key_len64;

  xpt_file_pos record_start = xpt_read_pos(state);
  xpt_file_pos_t rec_offset = xpt_read_offset(state);

  // If we resync, it should be 1 bytes offset from prev sync marker
  xpt_file_pos resync_pos = file_pos_add(xpt_read_pos(state), 1);

  // sync marker comes before record
  uint32_t sync_val;
  rc = blkread_uint32(state, &sync_val);
  if (rc != ADLB_SUCCESS)
    return rc;

  if (sync_val != xpt_sync_marker)
  {
    // Can't do much if sync marker bad, try to continue
    DEBUG("Sync marker at record start doesn't match expected: %"PRIx32
          " vs %"PRIx32". Proceeding anyway", sync_val, xpt_sync_marker);
  }

  // Get crc
  rc = blkread_uint32(state, &crc);
  if (rc != ADLB_SUCCESS)
    return rc;

  DEBUG("Reading entry at offset %zi", rec_offset);

  Byte rec_len_enc[VINT_MAX_BYTES];
  // get record length from file reading byte-by-byte
  rc = blkread_vint(state, &rec_len64, rec_len_enc, &rec_len_encb);
  if (rc != ADLB_SUCCESS)
  {
    // distinguish between read error and decode error
    ERR_PRINTF("Could not decode record length from file\n");

    if (rec_len_encb > 0)
    {
      // Decoding error rather than I/O error
      // Try to get place again
      xpt_read_resync(state, resync_pos);
      return ADLB_NOTHING;
    }
    return ADLB_ERROR;
  }

  DEBUG("Record length %"PRId64, rec_len64);

  // sanity check for record length
  if(rec_len64 < 0 || rec_len64 > SIZE_MAX)
  {
    ERR_PRINTF("Out of range record length: %"PRId64"\n", rec_len64);

    xpt_read_resync(state, resync_pos);
    return ADLB_NOTHING;
  }

  // Reconstitute encoded vint for crc check
  uInt tmp = (uInt)vint_encode(rec_len64, rec_len_enc);
  assert(tmp == rec_len_encb);

  // Zero-length record indicates end of file.
  // NOTE: if we had a small hole at end of block that CRC+rec len
  // doesn't fit in, we would have detected end of file earlier when
  // trying to advance to next blok
  if (rec_len64 == 0)
  {
    // check crc of encoded record
    crc_calc = crc32(0L, Z_NULL, 0);
    crc_calc = crc32(crc_calc, rec_len_enc, (uInt)rec_len_encb);
    if (crc_calc != crc)
    {
      ERR_PRINTF("CRC check failed for record at offset %zi\n",
                  rec_offset - (xpt_file_pos_t)sizeof(crc));
      ERR_PRINTF("Computed CRC32: %lx Expected CRC32: %lx\n",
              (unsigned long)crc_calc, (unsigned long)crc);
      xpt_read_resync(state, resync_pos);
      return ADLB_NOTHING;
    }
    // This appears to be a valid end of file marker
    return ADLB_DONE;
  }

  // buffer too small: signal caller
  if (buffer->length < rec_len64)
  {
    // consider case where record length is corrupted: check CRC by
    // reading directly from file to avoid danger of allocating
    // too-big buffer.
    if (!check_crc(state, (int)rec_len64, crc, buffer))
    {
      ERR_PRINTF("CRC check failed for record at offset %zi\n",
                 rec_offset);
      // Bad record, get caller to call again
      xpt_read_resync(state, resync_pos);
      return ADLB_NOTHING;
    }
    // reset position to start of record for re-reading
    seek_file_pos(state, record_start);

    *key_len = (size_t) rec_len64;
    DEBUG("Buffer too small for record");
    return ADLB_RETRY;
  }

  xpt_file_pos_t data_offset = xpt_read_offset(state);

  // Load rest of record into caller buffer
  rc = blkread(state, buffer->data, (xpt_block_pos_t)rec_len64);
  if (rc != ADLB_SUCCESS)
    return rc;

  // Now we can check crc
  crc_calc = crc32(0L, Z_NULL, 0);
  crc_calc = crc32(crc_calc, rec_len_enc, (uInt)rec_len_encb);
  crc_calc = crc32(crc_calc, (Byte*)buffer->data, (uInt)rec_len64);
  if (crc_calc != crc)
  {
    ERR_PRINTF("CRC check failed for record at offset %zi\n",
                rec_offset - (xpt_file_pos_t)sizeof(crc));
    ERR_PRINTF("Computed CRC32: %lx Expected CRC32: %lx\n",
            (unsigned long)crc_calc, (unsigned long)crc);
    xpt_read_resync(state, resync_pos);
    return ADLB_NOTHING;
  }

  // CRC check passed: checkpoint record is probably intact
  int key_len_encb = vint_decode(buffer->data, (size_t)rec_len64,
                                 &key_len64);
  if (key_len_encb < 0)
  {
    ERR_PRINTF("Error decoding vint for key length\n");
    xpt_read_resync(state, resync_pos);
    return ADLB_NOTHING;
  }
  if (key_len64 < 0 || key_len64 > INT_MAX)
  {
    ERR_PRINTF("Out of range key length: %"PRId64"\n", key_len64);
    xpt_read_resync(state, resync_pos);
    return ADLB_NOTHING;
  }
  if (key_len64 > rec_len64 - key_len_encb)
  {
    ERR_PRINTF("Key length too long for record: %"PRId64" v. "
               "%"PRId64"\n", key_len64, rec_len64);
    xpt_read_resync(state, resync_pos);
    return ADLB_NOTHING;
  }

  DEBUG("Key length is %"PRId64, key_len64);

  *key_len = (size_t) key_len64;
  *val_len = (size_t) (rec_len64 - (int64_t)key_len_encb - key_len64);

  // Work out relative offsets of key/value data from record start
  size_t key_rel = (size_t) key_len_encb;
  size_t val_rel = key_rel + *key_len;
  *key = buffer->data + key_rel;
  *val = buffer->data + val_rel;
  *val_offset = data_offset + (xpt_file_pos_t)val_rel;

  return ADLB_SUCCESS;
}

static xpt_file_pos
xpt_read_pos(const xlb_xpt_read_state *state)
{
  xpt_file_pos pos;
  pos.block = state->curr_block;
  pos.block_pos = state->curr_block_pos;
  return pos;
}

/*
  Calculate the current offset in file being read.
 */
static xpt_file_pos_t
xpt_read_offset(const xlb_xpt_read_state *state)
{
  xpt_file_pos_t block_off = ((xpt_file_pos_t)state->curr_block) *
                              state->block_size;
  return block_off + state->curr_block_pos;
}

static adlb_code
seek_file_pos(xlb_xpt_read_state *state, xpt_file_pos pos)
{
  adlb_code rc;
  if (pos.block != state->curr_block)
  {
    // First move to correct block
    rc = block_read_move(state, pos.block);
    if (rc != ADLB_SUCCESS)
      return rc;
  }

  // Then seek within block
  xpt_file_pos_t off = ((xpt_file_pos_t)pos.block) * state->block_size +
                       pos.block_pos;
  DEBUG("Seek to block offset %"PRIu32" (file offset %zi)",
        pos.block_pos, off);
  int rc2 = fseeko(state->file, off, SEEK_SET);
  if (rc2 != 0)
  {
    ERR_PRINTF("Error seeking to offset %zi in file\n", off);
    state->end_of_stream = true;
    return ADLB_ERROR;
  }
  state->curr_block_pos = pos.block_pos;
  return ADLB_SUCCESS;
}

/*
  Try to find next record using sync markers after reading invalid record

  This is only called in the event of another error, so we
  Silently ignore any errors and allow them to be handled the
  next time a record is read.
 */
static void xpt_read_resync(xlb_xpt_read_state *state,
                            xpt_file_pos resync_point)
{
  adlb_code rc;
  uint32_t curr;

  DEBUG("Attempting to resync with file");
  // Move to previous marker, then seek forward
  rc = seek_file_pos(state, resync_point);
  if (rc != ADLB_SUCCESS)
    return;

  rc = blkread_uint32(state, &curr);
  if (rc != ADLB_SUCCESS)
    return;

  while (curr != xpt_sync_marker)
  {
    // Incrementally update sync marker
    unsigned char next_byte;
    rc = blkread(state, &next_byte, 1);
    if (rc != ADLB_SUCCESS)
      return;

    // Big-endian order
    curr = (curr << 8) + next_byte;
  }
}

/*
  Check crc by reading from file

  buffer: buffer to use
  crc: crc to compare against
  state: file to read from.  Read position will be moved by rec_len
        (or to eof)
  returns true if it matches, false if error encountered
 */
static bool check_crc(xlb_xpt_read_state *state, int rec_len,
                    uint32_t crc, adlb_buffer *buffer)
{
  assert(rec_len >= 0);
  assert(buffer->length >= 0);
  xpt_block_pos_t rec_len_u = (xpt_block_pos_t)rec_len;
  xpt_block_pos_t read_b = 0;
  uLong crc_calc = crc32(0L, Z_NULL, 0);
  while (read_b < rec_len_u)
  {
    xpt_block_pos_t remaining = rec_len_u - read_b;
    xpt_block_pos_t to_read =
          ((xpt_block_pos_t)buffer->length < remaining ?
           (xpt_block_pos_t)buffer->length : remaining);
    adlb_code ac;

    ac = blkread(state, buffer->data, to_read);
    if (ac != ADLB_SUCCESS)
    {
      return false;
    }
    crc_calc = crc32(crc_calc, (Byte*)buffer->data, (uInt)to_read);
    read_b += to_read;
  }
  if (crc_calc == crc)
  {
    return true;
  }
  else
  {
    ERR_PRINTF("Computed CRC32: %lx Expected CRC32: %lx\n",
                (unsigned long)crc_calc, (unsigned long)crc);
    return false;
  }
}

static adlb_code bufwrite(xlb_xpt_state *state,
                  const void *data, xpt_block_pos_t length)
{
  TRACE("bufwrite %"PRIu32" bytes", length);

  const char *data_ptr = (const char*)data;

  if (state->curr_block_pos == 0 && state->buffer_used == 0)
  {
    // Make sure magic number gets written in case where buffer
    // aligns with block start
    state->buffer[0] = xpt_magic_num;
    state->buffer_used++;
  }

  while (length > 0)
  {
    xpt_block_pos_t buffer_left = XLB_XPT_BUFFER_SIZE -
                                  state->buffer_used;
    TRACE("Buffer size: %i, buffer used: %ui buffer left: %ui",
          XLB_XPT_BUFFER_SIZE, state->buffer_used, buffer_left);

    if (buffer_left == 0)
    {
      // Make space
      adlb_code ac = flush_buffers(state);
      ADLB_CHECK(ac);
      continue;
    }

    bool append_magic_num = false;
    xpt_block_pos_t write_size = buffer_left < length ?
                                 buffer_left : length;

    xpt_file_pos after_buf_pos = xpt_get_file_pos(state, true);
    if (after_buf_pos.block_pos + write_size > XLB_XPT_BLOCK_SIZE)
    {
      // Make sure magic number gets written in case where buffer doesn't
      // align with block start
      append_magic_num = true;
      // Only append rest of block
      write_size = XLB_XPT_BLOCK_SIZE - after_buf_pos.block_pos;
    }

    TRACE("Append %ui bytes to write buffer (%ui already used)",
          write_size, state->buffer_used);
    memcpy(state->buffer + state->buffer_used, data_ptr, write_size);

    // Update buffer size before calling flush_buffers
    state->buffer_used += write_size;

    if (write_size == buffer_left)
    {
      adlb_code ac = flush_buffers(state);
      ADLB_CHECK(ac);
    }

    data_ptr += write_size;
    length -= write_size;

    if (append_magic_num)
    {
      // Previous logic should guarantee buffers not full
      assert(state->buffer_used < XLB_XPT_BUFFER_SIZE);
      state->buffer[state->buffer_used++] = xpt_magic_num;
    }
  }
  return ADLB_SUCCESS;
}

// write 32-bit unsigned in endian-independent way
static adlb_code bufwrite_uint32(xlb_xpt_state *state,
                                     uint32_t val)
{
  unsigned char buf[4];
  buf[0] = (unsigned char)((val >> 24) & 0xFF);
  buf[1] = (unsigned char)((val >> 16) & 0xFF);
  buf[2] = (unsigned char)((val >> 8) & 0xFF);
  buf[3] = (unsigned char)(val & 0xFF);

  return bufwrite(state, buf, 4);
}

static xpt_file_pos_t xpt_file_offset(xlb_xpt_state *state,
          bool after_buffered)
{
  xpt_file_pos pos = xpt_get_file_pos(state, after_buffered);

  return ((xpt_file_pos_t)pos.block * XLB_XPT_BLOCK_SIZE) +
         pos.block_pos;
}

/*
  Find the position add bytes offset from pos, accounting for
  blocking scheme.
 */
static xpt_file_pos file_pos_add(xpt_file_pos pos, xpt_file_pos_t add)
{
  assert(pos.block_pos < XLB_XPT_BLOCK_SIZE);
  while (pos.block_pos + add >= XLB_XPT_BLOCK_SIZE)
  {
    // Move to next block
    pos.block = next_block((xpt_rank_t)xlb_comm_size, pos.block);
    pos.block_pos = 0;
    xpt_block_pos_t block_left = XLB_XPT_BLOCK_SIZE - pos.block_pos;
    add -= block_left;
  }
  pos.block_pos += (xpt_block_pos_t)add;
  return pos;
}

static xpt_file_pos xpt_get_file_pos(xlb_xpt_state *state,
                                    bool after_buffered)
{
  xpt_file_pos result;
  if (after_buffered)
  {
    // May be in next block
    xpt_file_pos before_buffered = { .block = state->curr_block,
                         .block_pos = state->curr_block_pos };
    result = file_pos_add(before_buffered,
                          (xpt_file_pos_t)state->buffer_used);
  }
  else
  {
    // Before buffered data
    result.block = state->curr_block;
    result.block_pos = state->curr_block_pos;
  }
  return result;
}

/*
  Reads from file, returns appropriate adlb return code and updates
  file position in state.  Assumes we don't read across blocks.
 */
static adlb_code checked_fread(xlb_xpt_read_state *state, void *buf,
                               xpt_block_pos_t length)
{
  assert(state->file != NULL);
  size_t frrc = fread(buf, 1, length, state->file);

  // fread will return # of bytes consumed, 0 on error
  state->curr_block_pos += length;

  if (frrc == length)
  {
    return ADLB_SUCCESS;
  }

 if (feof(state->file))
 {
    DEBUG("Hit end of file in checked_fread");
    return ADLB_DONE;
 }

  ERR_PRINTF("Error reading from checkpoint file: %i\n",
             ferror(state->file));
  return ADLB_ERROR;
}

/*
  Read data that may be split across non-contiguous blocks in file.
 */
static adlb_code blkread(xlb_xpt_read_state *state, void *buf,
                                 xpt_block_pos_t length)
{
  assert(state->file != NULL);
  assert(state->curr_block_pos <= state->block_size);

  if (state->end_of_stream)
    return ADLB_DONE;

  char *buf_ptr = (char*)buf;

  adlb_code ac;
  while (length > 0)
  {
    xpt_block_pos_t block_left = state->block_size -
                                 state->curr_block_pos;
    if (block_left <= 0)
    {
      ac = block_read_advance(state);
      if (ac != ADLB_SUCCESS)
        return ac;
      block_left = state->block_size - state->curr_block_pos;
      assert(block_left > 0);
    }

    xpt_block_pos_t read_length = block_left < length ?
                                  block_left : length;
    ac = checked_fread(state, buf_ptr, read_length);
    if (ac == ADLB_DONE)
    {
      state->end_of_stream = true;
    }
    else if (ac != ADLB_SUCCESS)
    {
      return ac;
    }

    length -= read_length;
    buf_ptr += read_length;
  } while (length > 0);
  return ADLB_SUCCESS;
}

static adlb_code blkgetc(xlb_xpt_read_state *state, unsigned char *c)
{
  assert(state->file != NULL);
  assert(state->curr_block_pos <= state->block_size);

  if (state->end_of_stream)
    return ADLB_DONE;

  if (state->curr_block_pos >= state->block_size)
  {
    adlb_code ac = block_read_advance(state);
    if (ac != ADLB_SUCCESS)
      return ac;
  }
  int c2 = fgetc(state->file);
  if (c2 == EOF)
  {
    state->end_of_stream = true;
    if (feof(state->file))
    {
      return ADLB_DONE;
    }
    else
    {
      ERR_PRINTF("Error reading from checkpoint file: %i\n",
                  ferror(state->file));
      return ADLB_ERROR;
    }
  }
  state->curr_block_pos++;
  *c = (unsigned char)c2;
  return ADLB_SUCCESS;
}

static uint32_t parse_uint32(unsigned char buf[4])
{
  return (((uint32_t)buf[0]) << 24) +
            (((uint32_t)buf[1]) << 16) +
            (((uint32_t)buf[2]) << 8) +
            (uint32_t)buf[3];
}

static adlb_code checked_fread_uint32(xlb_xpt_read_state *state,
                                             uint32_t *data)
{
  unsigned char buf[sizeof(uint32_t)];
  adlb_code rc = checked_fread(state, buf, sizeof(buf));

  if (rc != ADLB_SUCCESS)
    return rc;

  *data = parse_uint32(buf);
  return ADLB_SUCCESS;
}

static adlb_code blkread_uint32(xlb_xpt_read_state *state,
                                       uint32_t *data)
{
  unsigned char buf[sizeof(uint32_t)];
  adlb_code rc = blkread(state, buf, sizeof(buf));

  if (rc != ADLB_SUCCESS)
    return rc;

  *data = parse_uint32(buf);
  return ADLB_SUCCESS;
}


/*
  Try to decode vint from file.
  If I/O error encountered, set consumed to -1.
  Otherwise set to actual consumed bytes
  encoded: buffer of VINT_MAX_BYTES to collect actual data read.
          May be NULL.
 */
static adlb_code blkread_vint(xlb_xpt_read_state *state,
                int64_t *data, unsigned char *encoded, int *consumed)
{
  adlb_code rc;
  unsigned char b;
  vint_dec vi;
  int vic;

  rc = blkgetc(state, &b);
  if (rc != ADLB_SUCCESS)
  {
    *consumed = -1;
    return rc;
  }

  if (encoded != NULL)
    encoded[0] = b;
  *consumed = 1;

  vic = vint_decode_start(b, &vi);
  if (vic == -1)
    return ADLB_ERROR;

  while (vic == 1)
  {
    if (*consumed == VINT_MAX_BYTES)
    {
      // too long to represent
      return ADLB_ERROR;
    }

    rc = blkgetc(state, &b);
    if (rc != ADLB_SUCCESS)
    {
      *consumed = -1;
      return rc;
    }

    if (encoded != NULL)
      encoded[*consumed] = b;
    (*consumed)++;
    vic = vint_decode_more(b, &vi);
    if (vic == -1)
      return ADLB_ERROR;
  }

  *data = vi.accum;
  return ADLB_SUCCESS;
}

#endif // XLB_ENABLE_XPT
