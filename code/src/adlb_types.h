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

/*
    External interface for clients that want to deal with
    direct representations of ADLB data types, including
    serialization and deserialization.

    The serialization and deserialization interfaces are
    design to support zero-copy and zero-malloc where possible,
    by returning internal pointers to data structures and
    by using caller-provided fixed buffers.
 */
#ifndef __ADLB_TYPES_H
#define __ADLB_TYPES_H

#include "adlb-defs.h"
#include "table.h"

#include <assert.h>
#include <stdlib.h>
#include <string.h>

// Declarations of individual data types
typedef int64_t adlb_int_t;
typedef double adlb_float_t;

// OSX GCC 4.2.1 does not have uint
// Cf. https://github.com/imatix/zguide/issues/35
#if __APPLE__ == 1
typedef unsigned int uint;
#endif

typedef struct
{
  char* value;
  int length; // Length including null terminator
} adlb_string_t;

typedef struct
{
  void* value;
  int length;
} adlb_blob_t;

typedef struct {
  adlb_datum_id status_id;
  adlb_datum_id filename_id;
  bool mapped;
} adlb_file_ref;

typedef struct {
  // type of container keys
  adlb_data_type_short key_type;
  // type of container values
  adlb_data_type_short val_type;
  // Map from subscript to member
  struct table* members;
} adlb_container;

// Forward declaration of incomplete struct type
typedef struct adlb_struct_s *adlb_struct_ptr;

// Forward declaration of incomplete multiset type
typedef struct adlb_multiset_s *adlb_multiset_ptr;


// Union that can hold any of the above types
typedef union
{
  adlb_int_t INTEGER;
  adlb_float_t FLOAT;
  adlb_string_t STRING;
  adlb_blob_t BLOB;
  adlb_container CONTAINER;
  adlb_datum_id REF;
  adlb_file_ref FILE_REF;

  // Multiset struct too big for enum, store pointer
  adlb_multiset_ptr MULTISET;

  // adlb struct stores datums inline, store pointer only
  adlb_struct_ptr STRUCT;
} adlb_datum_storage;

// Type of values stored in container members
typedef adlb_datum_storage* adlb_container_val;

/* Pointer to an array of datums */
typedef struct {
  adlb_datum_storage *arr;
  uint count;
} slice_chunk_t;

typedef struct {
  uint chunk_count;
  uint item_count;
  slice_chunk_t chunks[];
} adlb_slice_t;

typedef struct adlb_struct_s {
  adlb_struct_type type;
  adlb_datum_storage data[];
} adlb_struct;

/*
   Declare a struct type.  This function must be called on all
   processes separately.
   type: the index of the struct type
   field_count: number of fields
   field_types: types of fields
   field_names: names of fields
 */
adlb_data_code
ADLB_Declare_struct_type(adlb_struct_type type,
                    const char *type_name,
                    int field_count,
                    const adlb_data_type *field_types,
                    const char **field_names);

/*
   Retrieve info about struct type. Returns type error if not found.
 */
adlb_data_code
ADLB_Lookup_struct_type(adlb_struct_type type,
                  const char **type_name, int *field_count,
                  const adlb_data_type **field_types, char ***field_names);

// adlb_binary_data: struct to represent
typedef struct {
  const void *data; // Pointer to data, always filled in
  // Caller-owned pointer if we use caller's buffer or allocate memory
  void *caller_data;
  int length; // Length of packed data
} adlb_binary_data;

// Struct representing a buffer, such as one provided by caller of function
typedef struct {
  char *data;
  int length;
} adlb_buffer;

/*
   Get a packed representation, i.e. one in which the data is in
   contiguous memory.
   This will return an internal pointer if possible.
   If this isn't possible it uses the provided caller_buffer if large enough,
   or allocates memory
 */
adlb_data_code
ADLB_Pack(const adlb_datum_storage *d, adlb_data_type type,
          const adlb_buffer *caller_buffer,
          adlb_binary_data *result);

/*
  Pack a datum into a buffer, prefixing with size stored as vint,
  so that contiguously stored datums can be correctly extracted.
  This will use tmp_buf as temporary storage if needed, and resize
  output if needed.

  tmp_buf: temporary storage that datum may be serialized into if
           needed
  output: the output buffer to append to
  output_caller_buffer: set to false if we allocate a new buffer,   
                        untouched otherwise
  output_pos: current offset into output buffer, which is updated
            for any appended data
 */
adlb_data_code
ADLB_Pack_buffer(const adlb_datum_storage *d, adlb_data_type type,
        const adlb_buffer *tmp_buf, adlb_buffer *output,
        bool *output_caller_buffer, int *output_pos);

/*
   Take ownership of data, allocing new buffer if needed
 */
static inline adlb_data_code
ADLB_Own_data(const adlb_buffer *caller_buffer, adlb_binary_data *data);

/*
   Unpack data from buffer into adlb_datum_storage, allocating new
   memory if necessary.  The unpacked data won't hold any pointers
   into the buffer.
 */
adlb_data_code
ADLB_Unpack(adlb_datum_storage *d, adlb_data_type type,
            const void *buffer, int length);

/**
   Functions to pack and unpack data from buffers.
   This are in header so they can be inlined if necessary

   The copy parameter to Unpack functions controls whether pointers
   to buffer should be stored in result (if copy == false), or whether
   fresh memory should be allocated (if copy == true)
 */

/*
   Initialize a buffer of at least the given size.
   Caller can pass in initial buffer of fixed size, specified by
   caller_buffer but we will allocate a larger one if it
   is too small.

   This is declared inline in the header since there are a lot of
   special cases that can be optimized out in typical usage.
 */
static inline adlb_data_code
ADLB_Init_buf(const adlb_buffer *caller_buffer,
                             adlb_buffer *curr_buffer,
                             bool *using_caller_buf, int init_length);

/*
   Helper function to resize buffer, supporting caller-provided buffers
   buf: current buffer in use
   using_caller_buf: whether the buffer is a static caller-provided buffer
   int: minimum length required
 */
static inline adlb_data_code
ADLB_Resize_buf(adlb_buffer *buf, bool *using_caller_buf, int min_length);

static inline void
ADLB_Free_binary_data(adlb_binary_data *buffer);

// Helper macro for packing and unpacking data types with no additional memory
#define ADLB_PACK_SCALAR(d, result) {                                    \
  assert(result != NULL);                                                \
  assert(d != NULL);                                                     \
  result->data = d;                                                      \
  result->caller_data = NULL;                                            \
  result->length = (int)sizeof(*d);                                      \
  return ADLB_DATA_SUCCESS;                                              \
}

#define ADLB_UNPACK_SCALAR(d, data, length) { \
  assert(length == (int)sizeof(*d));        \
  memcpy(d, data, sizeof(*d));              \
  return ADLB_DATA_SUCCESS;                 \
}

static inline adlb_data_code
ADLB_Pack_integer(const adlb_int_t *d, adlb_binary_data *result)
{
  ADLB_PACK_SCALAR(d, result);
}

static inline adlb_data_code
ADLB_Unpack_integer(adlb_int_t *d, const void *data, int length)
{
  ADLB_UNPACK_SCALAR(d, data, length);
}

static inline adlb_data_code
ADLB_Pack_ref(const adlb_datum_id *d, adlb_binary_data *result)
{
  ADLB_PACK_SCALAR(d, result);
}

static inline adlb_data_code
ADLB_Unpack_ref(adlb_datum_id *d, const void *data, int length)
{
  ADLB_UNPACK_SCALAR(d, data, length);
}

static inline adlb_data_code
ADLB_Pack_file_ref(const adlb_file_ref *d, adlb_binary_data *result)
{
  ADLB_PACK_SCALAR(d, result);
}

static inline adlb_data_code
ADLB_Unpack_file_ref(adlb_file_ref *d, const void *data, int length)
{
  ADLB_UNPACK_SCALAR(d, data, length);
}

static inline adlb_data_code
ADLB_Pack_float(const adlb_float_t *d, adlb_binary_data *result)
{
  ADLB_PACK_SCALAR(d, result);
}

static inline adlb_data_code
ADLB_Unpack_float(adlb_float_t *d, const void *data, int length)
{
  ADLB_UNPACK_SCALAR(d, data, length);
}

static inline adlb_data_code
ADLB_Pack_string(const adlb_string_t *s, adlb_binary_data *result)
{
  // Check for malformed string
  assert(s->length >= 1);
  assert(s->value != NULL);
  assert(s->value[s->length-1] == '\0');

  result->caller_data = NULL;
  result->data = s->value;
  result->length = s->length;

  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Unpack_string(adlb_string_t *s, void *data, int length, bool copy)
{
  // Must be null-terminated
  assert(length >= 1 && ((char*)data)[length-1] == '\0');
  if (copy)
  {
    s->value = malloc((size_t)length);
    memcpy(s->value, data, (size_t)length);
  }
  else
  {
    s->value = data;
  }
  s->length = length;
  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Pack_blob(const adlb_blob_t *b, adlb_binary_data *result)
{
  // Check for malformed blob
  assert(b->length >= 0);
  assert(b->value != NULL);

  result->caller_data = NULL;
  result->data = b->value;
  result->length = b->length;

  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Unpack_blob(adlb_blob_t *b, void *data, int length, bool copy)
{
  // Must be null-terminated
  assert(length >= 0);
  if (copy)
  {
    b->value = malloc((size_t)length);
    memcpy(b->value, data, (size_t)length);
  }
  else
  {
    b->value = data;
  }
  b->length = length;
  return ADLB_DATA_SUCCESS;
}


// Header used for metadata about serialization
// The remainder of buffer has struct fields stored in order
typedef struct {
  adlb_struct_type type;
  // Offsets of fields within buffer relative to start of buffer
  int field_offsets[];
} adlb_packed_struct_hdr;

adlb_data_code
ADLB_Pack_struct(const adlb_struct *s, const adlb_buffer *caller_buffer,
                 adlb_binary_data *result);

adlb_data_code
ADLB_Unpack_struct(adlb_struct **s, const void *data, int length);

// Free any memory used
adlb_data_code
ADLB_Free_storage(adlb_datum_storage *d, adlb_data_type type);
/*
   Create string with human-readable representation of datum.
   Caller must free string.
   Note: this is currently not fast or robust, only used for
   printing debug information
  */
char *
ADLB_Data_repr(const adlb_datum_storage *d, adlb_data_type type);


static inline adlb_data_code
ADLB_Init_buf(const adlb_buffer *caller_buffer,
                             adlb_buffer *curr_buffer,
                             bool *using_caller_buf, int init_length)
{
  assert(curr_buffer != NULL);
  assert(using_caller_buf != NULL);
  bool buf_provided = caller_buffer != NULL;
  assert(!buf_provided || caller_buffer->length >= 0);

  if (!buf_provided || caller_buffer->length < init_length)
  {
    curr_buffer->data = malloc((size_t)init_length);
    curr_buffer->length = init_length;
    *using_caller_buf = false;
  }
  else
  {
    *curr_buffer = *caller_buffer;
    *using_caller_buf = true;
  }

  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Resize_buf(adlb_buffer *buf, bool *using_caller_buf, int min_length)
{
  int old_length = buf->length;
  if (min_length >= buf->length)
  {
    buf->length *= 2;
    // Ensure big enough
    if (buf->length < min_length)
    {
      buf->length = min_length;
    }
    // If caller-provided buffer, have to allocate new, otherwise
    // resize current
    if (*using_caller_buf)
    {
      void *old_data = buf->data;
      buf->data = malloc((size_t)buf->length);
      memcpy(buf->data, old_data, (size_t)old_length);
      *using_caller_buf = false;
    }
    else
    {
      buf->data = realloc(buf->data, (size_t)buf->length);
    }
    if (buf->data == NULL)
      return ADLB_DATA_ERROR_OOM;
  }
  return ADLB_DATA_SUCCESS;
}


static inline void
ADLB_Free_binary_data(adlb_binary_data *buffer)
{
  // Must free any memory allocated
  if (buffer->caller_data != NULL)
  {
    free(buffer->caller_data);
  }
}

static inline adlb_data_code
ADLB_Own_data(const adlb_buffer *caller_buffer, adlb_binary_data *data)
{
  assert(data->data != NULL);
  assert(data->length >= 0);

  if (data->length > 0 && data->caller_data == NULL)
  {
    // Need to move data to caller-owned buffer. Decide whether
    // we need can use caller-provided buffer
    if (caller_buffer != NULL && caller_buffer->length >= data->length)
    {
      data->caller_data = caller_buffer->data;
    }
    else
    {
      data->caller_data = malloc((size_t)data->length);
    }
    memcpy(data->caller_data, data->data, (size_t)data->length);
    data->data = data->caller_data;
  }
  return ADLB_DATA_SUCCESS;
}
#endif // __ADLB_TYPES_H
