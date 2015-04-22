#include "multiset.h"

#include <assert.h>
#include <stdio.h>
#include <string.h>

#include "data.h"
#include "data_cleanup.h"
#include "data_internal.h"

xlb_multiset *xlb_multiset_alloc(adlb_data_type elem_type)
{
  xlb_multiset *m;
  m = malloc(sizeof(*m));
  xlb_multiset_init(m, elem_type);
  return m;
}

void xlb_multiset_init(xlb_multiset *set, adlb_data_type elem_type) {
  set->elem_type = elem_type;
  set->chunks = NULL;
  set->chunk_count = 0;
  set->chunk_arr_size = 0;
  set->last_chunk_elems = XLB_MULTISET_CHUNK_SIZE;
}

// Work out the number of elements currently in chunk
static uint
chunk_len(xlb_multiset *set, uint chunk_num) {
  if (chunk_num < set->chunk_count - 1) {
    // not the last: should be full
    return XLB_MULTISET_CHUNK_SIZE;
  } else {
    return set->last_chunk_elems;
  }
}

uint xlb_multiset_size(const xlb_multiset *set) {
  // All full chunks have same size
  return (set->chunk_count - 1) * XLB_MULTISET_CHUNK_SIZE +
          set->last_chunk_elems;
}

adlb_data_code xlb_multiset_add(xlb_multiset *set, const void *data,
                                size_t length, adlb_refc refcounts,
                                const adlb_datum_storage **stored) {
  xlb_multiset_chunk *chunk = NULL;
  if (set->last_chunk_elems >= XLB_MULTISET_CHUNK_SIZE)
  {

    if (set->chunk_arr_size == set->chunk_count)
    {
      // resize chunk pointer array if needed
      if (set->chunk_arr_size == 0) {
        set->chunk_arr_size = 1; // Start off with a small array
      }
      else
      {
        set->chunk_arr_size = set->chunk_arr_size * 2;
      }
      DATA_REALLOC(set->chunks, set->chunk_arr_size);
    }

    chunk = malloc(sizeof(xlb_multiset_chunk));
    set->chunks[set->chunk_count++] = chunk;
    set->last_chunk_elems = 0;
  }
  else
  {
    chunk = set->chunks[set->chunk_count - 1];
  }
  adlb_datum_storage *elem = &chunk->arr[set->last_chunk_elems++];
  if (stored != NULL) {
    *stored = elem;
  }
  return ADLB_Unpack(elem, (adlb_data_type)set->elem_type, data, length,
                     refcounts);
}

adlb_data_code
xlb_multiset_cleanup(xlb_multiset *set, bool free_root, bool free_mem,
             bool release_read, bool release_write,
             xlb_refc_acquire to_acquire, xlb_refc_changes *refcs)
{
  // Can't support subscripts
  assert(ADLB_REFC_IS_NULL(to_acquire.refcounts) ||
         !adlb_has_sub(to_acquire.subscript));
  for (uint i = 0; i < set->chunk_count; i++) {
    xlb_multiset_chunk *chunk = set->chunks[i];
    uint clen = chunk_len(set, i);
    for (int j = 0; j < clen; j++) {
      adlb_datum_storage *d = &chunk->arr[j];
      adlb_data_code dc;
      dc = xlb_datum_cleanup(d, (adlb_data_type)set->elem_type,
                    free_mem, release_read, release_write,
                    to_acquire, refcs);
      DATA_CHECK(dc);
    }
    if (free_mem)
      free(set->chunks[i]);
  }
  if (free_mem)
    free(set->chunks);
  // Optionally free root of data structure
  if (free_root)
    free(set);
  return ADLB_DATA_SUCCESS;
}

/*
 * Return a slice of count elements of the set from i (inclusive).
 * Note that returned pointers are internal and will be invalid
 * if multiset is modified.
 * TODO: is this needed
 */
adlb_data_code xlb_multiset_slice(xlb_multiset *set, uint start, uint count,
                                  adlb_slice_t **res) {
  // TODO: check ranges

  if (count == 0) {
    *res = malloc(sizeof(adlb_slice_t));
    (*res)->chunk_count = 0;
    (*res)->item_count = 0;
    return ADLB_DATA_SUCCESS;
  }

  // Allocate enough space for chunk pointers
  size_t max_chunks = (size_t)(count / XLB_MULTISET_CHUNK_SIZE + 1);
  adlb_slice_t *slice = malloc(sizeof(adlb_slice_t) +
                               sizeof(slice_chunk_t) * max_chunks);


  // First chunk
  uint chunk_ix = start / XLB_MULTISET_CHUNK_SIZE;
  uint pos_in_chunk = start % XLB_MULTISET_CHUNK_SIZE;

  uint out_chunk_count = 0;
  uint running_item_count = 0;
  while (running_item_count < count && chunk_ix < set->chunk_count) {
    uint clen = chunk_len(set, chunk_ix);
    xlb_multiset_chunk *chunk = set->chunks[chunk_ix];
    slice_chunk_t *out_chunk = &slice->chunks[out_chunk_count];
    uint remaining = (uint)(count - running_item_count);
    out_chunk->arr = &chunk->arr[pos_in_chunk];
    out_chunk->count = clen < remaining ? clen : remaining;

    // Update counts
    running_item_count += out_chunk->count;
    out_chunk_count++;
    chunk_ix++;
    pos_in_chunk = 0;
  }

  slice->chunk_count = out_chunk_count;
  slice->item_count = running_item_count;
  *res = slice;
  return ADLB_DATA_SUCCESS;
}

/*
  start: start at this offfset
  count: return at most this number
 */
adlb_data_code
xlb_multiset_extract_slice(xlb_multiset *set, int start, int count,
              const adlb_buffer *caller_buffer, adlb_buffer *output)
{
  assert(start >= 0);
  assert(count >= 0);
  adlb_data_code dc;
  bool use_caller_buf;

  adlb_data_type elem_type = (adlb_data_type)set->elem_type;

  dc = ADLB_Init_buf(caller_buffer, output, &use_caller_buf, 65536);
  DATA_CHECK(dc);

  size_t output_pos = 0; // Amount of output used
  int c = 0; // Count of members added to result

  // Allocate some temporary storage on stack
  adlb_buffer tmp_buf;
  tmp_buf.length = 4096;
  char tmp_storage[4096];
  tmp_buf.data = tmp_storage;


  // Calculate position of start
  uint chunk_ix = (uint)start / XLB_MULTISET_CHUNK_SIZE;
  uint pos_in_chunk = (uint)start % XLB_MULTISET_CHUNK_SIZE;
  while (c < count && chunk_ix < set->chunk_count) {
    xlb_multiset_chunk *chunk = set->chunks[chunk_ix];
    // Find size of current chunk.
    uint chunk_elems = chunk_len(set, chunk_ix);

    while (c < count && pos_in_chunk < chunk_elems) {
      // Append element to buffer
      adlb_datum_storage *elem = &chunk->arr[pos_in_chunk];
      dc = ADLB_Pack_buffer(elem, elem_type, true, &tmp_buf,
                output, &use_caller_buf, &output_pos);
      DATA_CHECK(dc);
      c++;
      pos_in_chunk++;
    }

    // Update to point at start of next chunk
    chunk_ix++;
    pos_in_chunk = 0;
  }

  // Record actual length of output
  output->length = output_pos;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_multiset_repr(xlb_multiset *set, char **repr)
{
  adlb_data_code dc;
  size_t res_len = 1024;
  char *res = malloc(res_len);
  int res_pos = 0;
  bool first = true;

  res_pos += sprintf(&res[res_pos], "{");

  for (uint chunk_ix = 0; chunk_ix < set->chunk_count; chunk_ix++)
  {
    xlb_multiset_chunk *chunk = set->chunks[chunk_ix];
    // Find size of current chunk.
    uint chunk_elems = chunk_len(set, chunk_ix);
    for (int i = 0; i < chunk_elems; i++)
    {
      char *elem_s = ADLB_Data_repr(&chunk->arr[i],
                  (adlb_data_type)set->elem_type);
      size_t elem_strlen = strlen(elem_s);
      // String plus additional chars
      size_t elem_repr_len = elem_strlen + 4;


      dc = xlb_resize_str(&res, &res_len, res_pos, elem_repr_len);
      DATA_CHECK(dc);

      if (first)
      {
        first = false;
      }
      else
      {
        res_pos += sprintf(&res[res_pos], ", ");
      }
      res_pos += sprintf(&res[res_pos], "{%s}", elem_s);
      free(elem_s);
    }
  }

  dc = xlb_resize_str(&res, &res_len, res_pos, 1);
  DATA_CHECK(dc);

  res_pos += sprintf(&res[res_pos], "}");

  *repr = res;
  return ADLB_DATA_SUCCESS;
}
