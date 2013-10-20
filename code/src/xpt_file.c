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

typedef struct
{
  uint32_t block;
  uint32_t block_pos;
} xpt_file_pos;

static inline bool is_init(xlb_xpt_state *state);

// Functions to select blocks for a rank
static inline uint32_t first_block(uint32_t rank, uint32_t ranks);
static inline uint32_t next_block(uint32_t ranks, uint32_t curr);

static inline adlb_code block_move_next(xlb_xpt_state *state);
static inline adlb_code block_move(uint32_t block,
            const char *filename, xlb_xpt_state *state);
static inline adlb_code xlb_xpt_next_block(xlb_xpt_state *state,
        off_t block_remaining);

// Primitives for reading and writing to blocked files
static inline adlb_code bufwrite(xlb_xpt_state *state,
                  const void *data, size_t length);
static inline adlb_code bufwrite_uint32(xlb_xpt_state *state,
                                     uint32_t val);
static inline adlb_code blkgetc(xlb_xpt_read_state *state, unsigned char *c);
static inline adlb_code blkread(xlb_xpt_read_state *state, void *buf,
                                 size_t length);
static inline adlb_code blkread_uint32(xlb_xpt_read_state *state,
                                       uint32_t *data);
static inline adlb_code blkread_vint(xlb_xpt_read_state *state,
                       int64_t *data, int *consumed);


static inline off_t xpt_file_offset(xlb_xpt_state *state,
                                    bool after_buffered);
static xpt_file_pos get_file_pos (xlb_xpt_read_state *state);
static adlb_code seek_file_pos(xlb_xpt_read_state *state, xpt_file_pos pos);
static inline adlb_code flush_buffers(xlb_xpt_state *state);

static inline bool is_xpt_leader(void);
static inline adlb_code xpt_header_read(bool read_hdr,
            xlb_xpt_read_state *state, const char *filename);
static inline adlb_code xpt_header_write(xlb_xpt_state *state);
static inline bool check_crc(xlb_xpt_read_state *state, int rec_len,
                             uint32_t crc, adlb_buffer *buffer);
static void xpt_read_resync(xlb_xpt_read_state *state,
                            xpt_file_pos resync_point);
static inline adlb_code block_read_advance(xlb_xpt_read_state *state);
static inline adlb_code block_read_move(xlb_xpt_read_state *state,
                        uint32_t new_block);

adlb_code xlb_xpt_init(const char *filename, xlb_xpt_state *state)
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

  uint32_t block = first_block((uint32_t)xlb_comm_rank,
                               (uint32_t)xlb_comm_size);
  adlb_code rc = block_move(block, filename, state);
  ADLB_CHECK(rc);

  if (is_xpt_leader())
  {
    rc = xpt_header_write(state);
    ADLB_CHECK(rc);
  }

  return ADLB_SUCCESS;
}

static inline bool is_init(xlb_xpt_state *state)
{
  return state->fd >= 0;
}

adlb_code xlb_xpt_close(xlb_xpt_state *state)
{
  assert(is_init(state));
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

static inline uint32_t first_block(uint32_t rank, uint32_t ranks)
{
  return rank;
}

static inline uint32_t next_block(uint32_t ranks, uint32_t curr)
{
  return curr + ranks;
}

/* Move to start of next block
 block_remaining: how many empty bytes at end of curr block?
 */
static inline adlb_code xlb_xpt_next_block(xlb_xpt_state *state,
        off_t block_remaining)
{
  assert(is_init(state));

  // Make sure contents written to file  
  if (state->buffer_used > 0)
  {
    adlb_code code = flush_buffers(state);
    ADLB_CHECK(code);
  }
  adlb_code rc = block_move_next(state);
  ADLB_CHECK(rc);
  return ADLB_SUCCESS;
}

/*
  Flush internal buffers
 */
static inline adlb_code flush_buffers(xlb_xpt_state *state)
{
  assert(is_init(state));
  assert(state->buffer_used <= XLB_XPT_BUFFER_SIZE);
  assert(state->curr_block_pos < XLB_XPT_BLOCK_SIZE);

  const unsigned char *buf_pos = state->buffer;
  size_t buf_left = state->buffer_used;
  while (buf_left > 0)
  {
    off_t curr_pos = xpt_file_offset(state, false);
    size_t block_left = XLB_XPT_BLOCK_SIZE - state->curr_block_pos;
    size_t write_size = block_left < buf_left ? block_left : buf_left;

    if (write_size > 0)
    {
      ssize_t pwc = pwrite(state->fd, buf_pos, write_size, curr_pos);
      CHECK_MSG(pwc == write_size, "Error writing to checkpoint file at "
              "offset %llu", (long long unsigned)curr_pos);
              
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
static inline adlb_code block_move_next(xlb_xpt_state *state)
{
  // Round-robin block allocation for now
  uint32_t block = next_block((uint32_t)xlb_comm_size, state->curr_block);
  return block_move(block, NULL, state);
}

/*
   Move to start of block.
   filename can be provided for error messages
 */
static inline adlb_code block_move(uint32_t block, 
        const char *filename, xlb_xpt_state *state)
{
  assert(is_init(state));

  DEBUG("Rank %i moving to start of block %i", xlb_comm_rank,
        state->curr_block);
  state->curr_block = block;
  state->curr_block_start = ((off_t)block) * XLB_XPT_BLOCK_SIZE;
  state->curr_block_pos = 0;
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
  assert(is_init(state));
  assert(state->curr_block == 0);
  assert(state->curr_block_pos == 0);
  assert(state->buffer_used == 0);

  adlb_code rc;

  // Write info about structure of checkpoint file
  rc = bufwrite_uint32(state, XLB_XPT_BLOCK_SIZE);
  ADLB_CHECK(rc);
  rc = bufwrite_uint32(state, (uint32_t)xlb_comm_size);
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
adlb_code xlb_xpt_write(const void *key, int key_len, const void *val,
                int val_len, xlb_xpt_state *state, off_t *val_offset)
{
  DEBUG("Writing entry to checkpoint file key_len: %i, val_len: %i, "
        "Block: %i", key_len, val_len, state->curr_block);
  assert(is_init(state));
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

  // Calculate CRC from components
  uLong crc = crc32(0L, Z_NULL, 0);
  crc = crc32(crc, rec_len_enc, rec_len_encb);
  crc = crc32(crc, key_len_enc, key_len_encb);
  crc = crc32(crc, key, (uInt)key_len);
  crc = crc32(crc, val, (uInt)val_len);

  TRACE("CRC: %lx", crc);

  DEBUG("Writing checkpoint entry at offset %llu", (long long unsigned)
          xpt_file_offset(state, true)); 
  
  // Write out all data in sequence
  // First write sync marker
  rc = bufwrite_uint32(state, xpt_sync_marker);
  ADLB_CHECK(rc);

  rc = bufwrite_uint32(state, (uint32_t)crc);
  ADLB_CHECK(rc);

  rc = bufwrite(state, rec_len_enc, rec_len_encb);
  ADLB_CHECK(rc);

  rc = bufwrite(state, key_len_enc, key_len_encb);
  ADLB_CHECK(rc);

  rc = bufwrite(state, key, key_len);
  ADLB_CHECK(rc);

  if (val_offset != NULL)
  {
    // Return offset of value in file if needed
    *val_offset = xpt_file_offset(state, true);
  }

  rc = bufwrite(state, val, val_len);
  ADLB_CHECK(rc);

  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_read_val(char *file, off_t val_offset, int val_len,
                           xlb_xpt_state *state, void *buffer)
{
  if (file == NULL)
  {
    // checkpoint is in file currently being written.
    assert(is_init(state));
    assert(val_len >= 0);

    // TODO: it would be better to reread entire record to make sure
    //       we don't get a corrupted record.
    
    uint32_t block = (uint32_t) (val_offset / XLB_XPT_BLOCK_SIZE);
    uint32_t block_pos = (uint32_t) (val_offset % XLB_XPT_BLOCK_SIZE);
    void *buf_pos = buffer;
    size_t left = (size_t)val_len;
    DEBUG("Reading val %zu bytes @ offset %llu of current file",
          left, (long long unsigned) val_offset);
    while (left > 0)
    {
      off_t read_offset = block * XLB_XPT_BLOCK_SIZE + block_pos;
      off_t block_left = XLB_XPT_BLOCK_SIZE - block_pos;
      size_t to_read = block_left < val_len ? block_left : val_len;

      DEBUG("Read val chunk: %zu bytes @ %llu", to_read, 
            (long long unsigned)read_offset);

      if (to_read > 0)
      {
        size_t read = pread(state->fd, buf_pos, to_read, read_offset);
        if (read == 0)
        {
          ERR_PRINTF("Trying to read checkpoint value that is past end "
                     "of file: %zu bytes @ offset %llu\n", to_read, 
                     (long long unsigned)read_offset);
          return ADLB_ERROR;
        }
        CHECK_MSG(read == to_read, "Error reading back checkpoint value: "
                  "%d: %s", errno, strerror(errno));
              
        left -= to_read;
        buf_pos += to_read;
      }

      if (block_left == to_read)
      {
        // advance to next block
        block = next_block((uint32_t)xlb_comm_size, block);
        DEBUG("Reading val: move to next block %"PRIu32, block);
        block_pos = 0;
      }
    }

    return ADLB_SUCCESS;
  }
  else
  {
    ERR_PRINTF("DO NOT SUPPORT LOADING FROM OLD FILE %s YET\n", file);
    // TODO: open file for read
    // TODO: read_blocked primitive
    // fseek(fileptr, val_offset);
    // adlb_code ac = read_blocked(fileptr, buffer, val_len);
    return ADLB_ERROR;
  }
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
  state->curr_block_pos = 0;

  int magic_num = fgetc(state->file);
  CHECK_MSG(magic_num == xpt_magic_num, "Invalid magic number %i"
        " at start of checkpoint file %s: may be corrupted or not"
        " checkpoint", magic_num, filename);
  state->curr_block_pos++;

  adlb_code rc = xpt_header_read(true, state, filename);
  ADLB_CHECK(rc)
  DEBUG("Opened file %s block size %i ranks %i", filename, state->block_size,
          state->ranks);
  return ADLB_SUCCESS;
}

/*
  Read header from current position in file, assuming we're byte 2 of
  file (seek to start, then check magic number before calling this).
  read_hdr: if true, read values and check.  If false, just move past header 
 */
static inline adlb_code xpt_header_read(bool read_hdr,
            xlb_xpt_read_state *state, const char *filename)
{
  uint32_t block_size, ranks;
  // TODO: verify header checksum?
  adlb_code rc;
  rc = blkread_uint32(state, &block_size);
  CHECK_MSG(rc == ADLB_SUCCESS, "Error reading header");
  rc = blkread_uint32(state, &ranks);
  CHECK_MSG(rc == ADLB_SUCCESS, "Error reading header");
  if (read_hdr)
  {
    state->block_size = block_size;
    state->ranks = ranks;
    CHECK_MSG(state->block_size > 0, "Block size cannot be zero in file %s",
              filename);
    CHECK_MSG(state->ranks > 0, "Ranks cannot be zero in file %s",
              filename);
  }
  else
  {
    DEBUG("Skipped header for rank %i", state->curr_rank);
  }
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_close_read(xlb_xpt_read_state *state)
{
  assert(state->file != NULL);
  int rc = fclose(state->file);
  state->file = NULL;
  CHECK_MSG(rc == 0, "Error closing checkpoint file");
  return ADLB_SUCCESS;
}

adlb_code xlb_xpt_read_select(xlb_xpt_read_state *state, uint32_t rank)
{
  assert(state->file != NULL);
  DEBUG("Select rank %"PRIu32" for reading", rank);
  CHECK_MSG(rank >= 0 && rank < state->ranks, "Invalid rank: %"PRId32, rank);
  state->curr_rank = rank;
  uint32_t rank_block1 = first_block(state->curr_rank, state->ranks);
  adlb_code rc = block_read_move(state, rank_block1);
  if (rc != ADLB_SUCCESS)
  {
    ERR_PRINTF("Error moving to start of first block %"PRIu32" for rank %i\n",
                rank_block1, rank);
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
static inline adlb_code block_read_advance(xlb_xpt_read_state *state)
{
  assert(state->file != NULL);
  uint32_t new_block = next_block(state->ranks, state->curr_block);
  return block_read_move(state, new_block);
}

static inline adlb_code block_read_move(xlb_xpt_read_state *state,
                        uint32_t new_block)
{
  DEBUG("Moving from block %i to block %i for rank %i/%i", state->curr_block,
        new_block, state->curr_rank, state->ranks);
  state->curr_block = new_block;
  state->curr_block_pos = 0;
        
  off_t block_start = ((off_t)state->curr_block) * state->block_size;
  int rc = fseek(state->file, block_start, SEEK_SET);
  if (rc != 0)
  {
    if (feof(state->file))
    {
      return ADLB_DONE;
    }
    else
    {
      ERR_PRINTF("Error seeking to offset %llu in checkpoint file\n",
                     (long long unsigned)block_start);
      return ADLB_ERROR;
    }
  }

  int magic_num = fgetc(state->file);
  state->curr_block_pos++;
  if (magic_num == EOF || magic_num == 0) {
    DEBUG("Past last block in file %i for rank %i", state->curr_block,
                                                    state->curr_rank);
    return ADLB_DONE;
  }

  CHECK_MSG(magic_num == xpt_magic_num, "Invalid magic number %i"
        " at start of checkpoint block: may be corrupted", magic_num);
  if (state->curr_block == 0)
  {
    // Move past file header
    adlb_code rc = xpt_header_read(false, state, NULL);
    ADLB_CHECK(rc);
  }
  return ADLB_SUCCESS;

}

adlb_code xlb_xpt_read(xlb_xpt_read_state *state, adlb_buffer *buffer,
   int *key_len, void **key, int *val_len, void **val, off_t *val_offset)
{

  while (true) // Retry loop 
  {
    adlb_code rc;
    assert(state->file != NULL);
    assert(buffer->data != NULL);

    uint32_t crc;
    // Length in bytes of encoded vint values
    int rec_len_encb;
    int64_t rec_len64, key_len64;

    xpt_file_pos record_start = get_file_pos(state);
    off_t rec_offset = ((off_t)state->curr_block) * state->block_size +
                        state->curr_block_pos;

    // sync marker comes before record
    uint32_t sync;
    rc = blkread_uint32(state, &sync);
    if (rc != ADLB_SUCCESS)
      return rc;

    if (sync != xpt_sync_marker)
    {
      // TODO: need better way to detect end of file.

      // Can't do much if sync marker bad, try to continue
      DEBUG("Sync marker at start of record doesn't match expected: %"PRIx32
            " vs %"PRIx32". Proceeding anyway", sync, xpt_sync_marker);
    }
    
    // I we resync, it should be from this position after prev sync marker
    xpt_file_pos resync_pos = get_file_pos(state);

    // Get crc
    rc = blkread_uint32(state, &crc);
    if (rc != ADLB_SUCCESS)
      return rc;
    
    DEBUG("Reading entry at offset %llu", (long long unsigned)rec_offset);

    // get record length from file reading byte-by-byte
    rc = blkread_vint(state, &rec_len64, &rec_len_encb);
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
    if(rec_len64 < 0 || rec_len64 > INT_MAX)
    {
      ERR_PRINTF("Out of range record length: %"PRId64"\n", rec_len64);
      
      xpt_read_resync(state, resync_pos);
      return ADLB_NOTHING;
    }
  
    // buffer too small: signal caller
    if (buffer->length < rec_len64)
    {
      // consider case where record length is corrupted: check CRC by
      // reading directly from file to avoid danger of allocating
      // too-big buffer.
      if (!check_crc(state, (int)rec_len64, crc, buffer))
      {
        ERR_PRINTF("CRC check failed for record at offset %llu\n",
                    (long long unsigned)rec_offset);
        // Bad record, get caller to call again
        xpt_read_resync(state, resync_pos);
        return ADLB_NOTHING;
      }
      // reset position to start of record for re-reading
      seek_file_pos(state, record_start);

      *key_len = (int)rec_len64;
      DEBUG("Buffer too small for record");
      return ADLB_RETRY;
    }

    // Load rest of record into caller buffer
    rc = blkread(state, buffer->data, (size_t)rec_len64);
    if (rc != ADLB_SUCCESS)
      return rc;

    // Reconstitute encoded vint for crc check
    Byte rec_len_enc[VINT_MAX_BYTES];
    uInt tmp = (uInt)vint_encode(rec_len64, rec_len_enc);
    assert(tmp == rec_len_encb);

    // Now we can check crc 
    uLong crc_calc = crc32(0L, Z_NULL, 0);
    crc_calc = crc32(crc_calc, rec_len_enc, (uInt)rec_len_encb);
    crc_calc = crc32(crc_calc, (Byte*)buffer->data, (uInt)rec_len64);
    if (crc_calc != crc)
    {
      ERR_PRINTF("CRC check failed for record at offset %llu\n",
                  (long long unsigned)(rec_offset - sizeof(crc)));
      ERR_PRINTF("Computed CRC32: %lx Expected CRC32: %lx\n",
              (unsigned long)crc_calc, (unsigned long)crc);
      return ADLB_NOTHING;
    }

    // CRC check passed: checkpoint record is probably intact
    int key_len_encb = vint_decode(buffer->data, (int)rec_len64, &key_len64);
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
      ERR_PRINTF("Key length too long for record: %"PRId64" v. %"PRId64"\n",
                  key_len64, rec_len64);
      xpt_read_resync(state, resync_pos);
      return ADLB_NOTHING;
    }
    
    DEBUG("Key length is %"PRId64, key_len64);

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
}

static xpt_file_pos get_file_pos(xlb_xpt_read_state *state)
{
  xpt_file_pos pos;
  pos.block = state->curr_block;
  pos.block_pos = state->curr_block_pos;
  return pos;
}

static adlb_code seek_file_pos(xlb_xpt_read_state *state, xpt_file_pos pos)
{
  adlb_code rc;
  if (pos.block != state->curr_block)
  {
    // First move to correct block
    rc = block_read_move(state, pos.block);
    ADLB_CHECK(rc);
  }

  // Then seek within block
  off_t off = ((off_t)pos.block) * state->block_size + pos.block_pos;
  DEBUG("Seek to block offset %"PRIu32" (file offset %llu)", pos.block_pos,
        (long long unsigned) off);
  int rc2 = fseeko(state->file, off, SEEK_SET);
  if (rc2 != 0)
  {
    ERR_PRINTF("Error seeking to offset %llu in file\n",
               (long long unsigned)off);
    return ADLB_ERROR;
  }
  state->curr_block_pos = pos.block_pos;
  return ADLB_SUCCESS;
}

/*
  Try to find next record using sync markers after reading invalid record.

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
static inline bool check_crc(xlb_xpt_read_state *state, int rec_len,
                    uint32_t crc, adlb_buffer *buffer)
{
  int read = 0;
  uLong crc_calc = crc32(0L, Z_NULL, 0);
  while (read < rec_len)
  {
    int remaining = rec_len - read;
    size_t to_read = (size_t)(buffer->length < remaining ?
                              buffer->length : remaining);
    adlb_code ac;

    ac = blkread(state, buffer->data, to_read);
    if (ac != ADLB_SUCCESS)
    {
      return false;
    }
    crc_calc = crc32(crc_calc, (Byte*)buffer->data, (uInt)to_read);
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

static inline adlb_code bufwrite(xlb_xpt_state *state,
                  const void *data, size_t length)
{
  if (state->curr_block_pos == 0 && state->buffer_used == 0)
  {
    // Make sure magic number gets written in case where buffer
    // aligns with block start
    state->buffer[0] = xpt_magic_num;
    state->buffer_used++;
  }

  while (length > 0)
  {
    size_t buffer_left = XLB_XPT_BUFFER_SIZE - state->buffer_used;
    if (buffer_left == 0)
    {
      // Make space
      adlb_code ac = flush_buffers(state);
      ADLB_CHECK(ac);
      continue;
    }
   
    bool append_magic_num = false;
    size_t write_size = buffer_left < length ? buffer_left : length;
    if (state->curr_block_pos + state->buffer_used + write_size
          > XLB_XPT_BLOCK_SIZE)
    {
      // Make sure magic number gets written in case where buffer doesn't
      // align with block start
      append_magic_num = true;
      // Only append rest of block
      write_size = XLB_XPT_BLOCK_SIZE - state->curr_block_pos - state->buffer_used;
    }

    memcpy(state->buffer + state->buffer_used, data, write_size);

    data += write_size;
    length -= write_size;

    if (write_size == buffer_left)
    {
      adlb_code ac = flush_buffers(state);
      ADLB_CHECK(ac);
    }
    else
    {
      state->buffer_used += write_size;
      if (append_magic_num)
      {
        state->buffer[state->buffer_used++] = xpt_magic_num;
      }
    }
  }
  return ADLB_SUCCESS;
}

// write 32-bit unsigned in endian-independent way
static inline adlb_code bufwrite_uint32(xlb_xpt_state *state,
                                     uint32_t val)
{
  unsigned char buf[4];
  buf[0] = (unsigned char)((val >> 24) & 0xFF);
  buf[1] = (unsigned char)((val >> 16) & 0xFF);
  buf[2] = (unsigned char)((val >> 8) & 0xFF);
  buf[3] = (unsigned char)(val & 0xFF);

  return bufwrite(state, buf, 4);
}


static inline off_t xpt_file_offset(xlb_xpt_state *state,
          bool after_buffered)
{
  if (after_buffered)
  {
    // May be in next block
    uint32_t block = state->curr_block;
    uint32_t block_pos = state->curr_block_pos;
    uint32_t buf_left = (uint32_t)state->buffer_used;

    while (buf_left > 0)
    {
      // Move to next blocks
      uint32_t block_left = XLB_XPT_BLOCK_SIZE - block_pos;
      uint32_t advance = block_left < buf_left ? block_left : buf_left;
      if (advance <= block_left)
      {
        block_pos += advance;
      }
      else
      {
        block = next_block((uint32_t)xlb_comm_size, block);
        block_pos = 0;
      }
      buf_left -= advance;
    }

    return ((off_t)block * XLB_XPT_BLOCK_SIZE) + block_pos;
  }
  else
  {
    // Before buffered data
    return state->curr_block_start + state->curr_block_pos;
  }
}

static inline adlb_code blkread(xlb_xpt_read_state *state, void *buf,
                                 size_t length)
{
  assert(state->file != NULL);
  assert(state->curr_block_pos >= 0);
  assert(state->curr_block_pos <= state->block_size);
  while (length > 0)
  {
    size_t block_left = state->block_size - state->curr_block_pos;
    if (block_left <= 0)
    {
      adlb_code ac = block_read_advance(state);
      if (ac != ADLB_SUCCESS)
        return ac;
      block_left = state->block_size - state->curr_block_pos;
      assert(block_left > 0);
    }

    size_t read_length = block_left < length ? block_left : length;
    size_t frrc = fread(buf, 1, read_length, state->file);
    
    if (frrc != read_length)
    {
      if (feof(state->file))
        return ADLB_DONE;
      else
      {
        printf("Error reading from checkpoint file: %i\n",
              ferror((state)->file));
        return ADLB_ERROR;
      }
    }

    state->curr_block_pos += read_length;
    length -= read_length;
    buf += read_length;
  } while (length > 0);
  return ADLB_SUCCESS;
}

static inline adlb_code blkgetc(xlb_xpt_read_state *state, unsigned char *c)
{
  assert(state->file != NULL);
  assert(state->curr_block_pos >= 0);
  assert(state->curr_block_pos <= state->block_size);
  if (state->curr_block_pos >= state->block_size)
  {
    adlb_code ac = block_read_advance(state);
    if (ac != ADLB_SUCCESS)
      return ac;
  }
  int c2 = fgetc(state->file);
  if (c2 == EOF)
  {
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

static inline adlb_code blkread_uint32(xlb_xpt_read_state *state,
                                       uint32_t *data)
{
  unsigned char buf[4];
  adlb_code rc = blkread(state, buf, sizeof(buf));

  if (rc != ADLB_SUCCESS)
    return rc;

  *data = (((uint32_t)buf[0]) << 24) +
          (((uint32_t)buf[1]) << 16) +
          (((uint32_t)buf[2]) << 8) +
          (uint32_t)buf[3];
  return ADLB_SUCCESS;
}


/*
  Try to decode vint from file.
  If I/O error encountered, set consumed to -1.
  Otherwise set to actual consumed bytes
 */
static inline adlb_code blkread_vint(xlb_xpt_read_state *state,
                            int64_t *data, int *consumed)
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
  *consumed = 1;

  vic = vint_decode_start(b, &vi);
  if (vic == -1)
    return ADLB_ERROR;

  while (vic == 1)
  {
    rc = blkgetc(state, &b);
    if (rc != ADLB_SUCCESS)
    {
      *consumed = -1;
      return rc;
    }
    
    (*consumed)++;
    vic = vint_decode_more(b, &vi);
    if (vic == -1)
      return ADLB_ERROR;
  }

  *data = vi.accum;
  return ADLB_SUCCESS;
}

#endif // XLB_ENABLE_XPT
