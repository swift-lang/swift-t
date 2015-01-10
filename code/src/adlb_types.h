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
#include "table_bp.h"

#include <assert.h>
#include <inttypes.h>
#include <stdio.h>
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
  size_t length; // Length including null terminator
} adlb_string_t;

typedef struct
{
  void* value;
  size_t length;
} adlb_blob_t;

typedef struct {
  adlb_datum_id id;
  // Current count of references held
  int read_refs;
  int write_refs;
} adlb_ref;

typedef struct {
  // type of container keys
  adlb_data_type_short key_type;
  // type of container values
  adlb_data_type_short val_type;
  // Map from subscript to member.  Use binary subscripts as keys.
  // This means that the binary representations of keys needs to
  // follow expected equality rules.
  struct table_bp* members;
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
  adlb_ref REF;

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
  uint32_t count;
} slice_chunk_t;

typedef struct {
  uint32_t chunk_count;
  uint32_t item_count;
  slice_chunk_t chunks[];
} adlb_slice_t;

typedef struct {
  adlb_datum_storage data;
  /* If initialized, valid data is stored in field */
  bool initialized : 1;
  /* Flag that indicates something has reserved this field.
     Typically remains false but used for special purposes. */
  bool reserved : 1;
} adlb_struct_field;

typedef struct adlb_struct_s {
  adlb_struct_type type;
  adlb_struct_field fields[];
} adlb_struct;

typedef struct {
  adlb_data_type type;
  adlb_type_extra extra;
} adlb_struct_field_type;

/*
   Declare a struct type.  This function must be called on all
   processes separately.
   type: the index of the struct type
   type_name: name of the type, which must not conflict with an
         existing adlb type (e.g. int), or an already-declared struct
         type.  This can be later used to create structs of that type.
   field_count: number of fields
   field_types: types of fields
   field_names: names of fields
 */
adlb_data_code
ADLB_Declare_struct_type(adlb_struct_type type,
                    const char *type_name,
                    int field_count,
                    const adlb_struct_field_type *field_types,
                    const char **field_names);

/*
   Retrieve info about struct type. Returns type error if not found.
   Any pointer arguments can be left NULL if info not needed.
 */
adlb_data_code
ADLB_Lookup_struct_type(adlb_struct_type type,
                  const char **type_name, int *field_count,
                  const adlb_struct_field_type **field_types,
                  char const* const** field_names);

// adlb_binary_data: struct to represent
typedef struct {
  const void *data; // Pointer to data, always filled in
  // Caller-owned pointer if we use caller's buffer or allocate memory
  void *caller_data;
  size_t length; // Length of packed data
} adlb_binary_data;

// Struct representing a buffer, such as one provided by caller of function
typedef struct {
  char *data;
  size_t length;
} adlb_buffer;

/**
  Initialize data types module.  Required to look up types by name.
 */
adlb_data_code xlb_data_types_init(void);

/**
  Add a new data type.
 */
adlb_data_code xlb_data_type_add(const char *name,
            adlb_data_type code, adlb_type_extra extra);

/**
  Lookup a data type.  If not found, set type to ADLB_DATA_TYPE_NULL.
 */
adlb_data_code xlb_data_type_lookup(const char* name,
        adlb_data_type* type, adlb_type_extra *extra);

/**
  Finalize data types module and clean up memory.
 */
void xlb_data_types_finalize(void);

/**
 * Return true if the data type is a compound type that has multiple
 * assignable subscripts
 */
static inline bool ADLB_Data_is_compound(adlb_data_type type)
{
  switch (type)
  {
    case ADLB_DATA_TYPE_CONTAINER:
    case ADLB_DATA_TYPE_MULTISET:
    case ADLB_DATA_TYPE_STRUCT:
      return true;
    default:
      return false;
  }
}

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
  Append data to a buffer, prefixing with size stored as vint,
  so that contiguously stored entries can be correctly extracted.
  This will use tmp_buf as temporary storage if needed, and resize
  output if needed.
  type: this must be provided if data is to interpreted as a particular
        ADLB data type upon decoding.
 */
adlb_data_code
ADLB_Append_buffer(adlb_data_type type, const void *data, size_t length,
        bool prefix_len, adlb_buffer *output, bool *output_caller_buffer,
        size_t *output_pos);


/*
  Pack a datum into a buffer.
  This will use tmp_buf as temporary storage if needed, and resize
  output if needed.

  prefix_len: include prefix with size stored as vint, so that
      contiguously stored datums can be correctly extracted.
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
        bool prefix_len, const adlb_buffer *tmp_buf, adlb_buffer *output,
        bool *output_caller_buffer, size_t *output_pos);

/*
  Whether we pad the vint size to VINT_MAX_BYTES when appending
  to buffer;
 */
bool ADLB_pack_pad_size(adlb_data_type type);

/*
   Take ownership of data, allocing new buffer if needed
   caller_buffer: optional space to store data, can be NULL
 */
static inline adlb_data_code
ADLB_Own_data(const adlb_buffer *caller_buffer, adlb_binary_data *data);

/*
   Unpack data from buffer into adlb_datum_storage, allocating new
   memory if necessary.  The unpacked data won't hold any pointers
   into the buffer.  Compound data types are initialized.

   refcounts: number of refcounts to initialize any refs inside
              the unpacked data structure
 */
adlb_data_code
ADLB_Unpack(adlb_datum_storage *d, adlb_data_type type,
            const void *buffer, size_t length, adlb_refc refcounts);

/*
  Same as ADLB_Unpack, with more options.
  Buffer cannot be const since we may take ownership of buffer.

  copy_buffer: If false, buffer must not be modified.  If true,
      function can optionally take ownership of buffer.  If the
      function takes ownership, the buffer will be freed when the
      datum is freed.
  init_compound: if true, compound data types (containers, structs, etc)
  that support incremental stores are assumed to be initialized and
  shouldn't be reinitialized.
  took_ownership: Set to indicate whether the function took ownership
        of the buffer.  Can be left as NULL.  Always false if
        copy_buffer is true.
 */
adlb_data_code
ADLB_Unpack2(adlb_datum_storage *d, adlb_data_type type,
          void *buffer, size_t length, bool copy_buffer,
          adlb_refc refcounts, bool init_compound, bool *took_ownership);

/*
  Helper to unpack data from buffer.  This will simply
  find the packed data for the next item in the buffer and
  advance the read position
  buffer, length: buffer packed with ADLB_Pack_buffer
  type: data type, or ADLB_DATA_TYPE_NULL if uninterpreted
  pos: input/output.  Caller provides current read position in buffer,
        this function sets it to the start of the next entry
  entry, entry_length: output. packed field data
  returns: ADLB_DATA_SUCCESS when field successfully returned,
           ADLB_DONE when exhausted,
           ADLB_DATA_INVALID if not able to decode
 */
adlb_data_code
ADLB_Unpack_buffer(adlb_data_type type,
        const void *buffer, size_t length, size_t *pos,
        const void **entry, size_t* entry_length);

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
                             bool *using_caller_buf, size_t init_length);

/*
   Helper function to resize buffer, supporting caller-provided buffers
   buf: current buffer in use
   using_caller_buf: whether the buffer is a static caller-provided buffer
   int: minimum length required
 */
static inline adlb_data_code
ADLB_Resize_buf(adlb_buffer *buf, bool *using_caller_buf, size_t min_length);

static inline void
ADLB_Free_buf(adlb_buffer *buf, bool using_caller_buf);

static inline void
ADLB_Free_binary_data(adlb_binary_data *buffer);

// Helper macro for packing and unpacking data types with no additional memory
#define ADLB_PACK_SCALAR(d, result) {     \
  assert((result) != NULL);               \
  assert((d) != NULL);                    \
  (result)->data = (d);                   \
  (result)->caller_data = NULL;           \
  (result)->length = (int)sizeof(*(d));   \
}

#define ADLB_UNPACK_SCALAR(d, data, length) {           \
  if (length != sizeof(*(d)))                           \
  {                                                     \
    printf("Could not unpack: expected length "         \
        "%zu actual length %zu", sizeof(*(d)), length); \
    return ADLB_DATA_ERROR_INVALID;                     \
  }                                                     \
  memcpy((d), data, sizeof(*(d)));                      \
}

/**
  Initialize a compound data type
  must_init: if true, fail if cannot be initialized, e.g. if we don't
             have full type info.
 */
adlb_data_code
ADLB_Init_compound(adlb_datum_storage *d, adlb_data_type type,
          adlb_type_extra type_extra, bool must_init);

static inline adlb_data_code
ADLB_Pack_integer(const adlb_int_t *d, adlb_binary_data *result)
{
  ADLB_PACK_SCALAR(d, result);
  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Unpack_integer(adlb_int_t *d, const void *data, size_t length)
{
  ADLB_UNPACK_SCALAR(d, data, length);
  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Pack_ref(const adlb_ref *d, adlb_binary_data *result)
{
  ADLB_PACK_SCALAR(d, result);
  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Unpack_ref(adlb_ref *d, const void *data, size_t length,
                adlb_refc refcounts, bool overwrite_refcounts)
{
  ADLB_UNPACK_SCALAR(d, data, length);

  if (overwrite_refcounts) {
    // Overwrite refcounts with provided refcounts
    d->read_refs = refcounts.read_refcount;
    d->write_refs = refcounts.write_refcount;
  }
  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Pack_float(const adlb_float_t *d, adlb_binary_data *result)
{
  ADLB_PACK_SCALAR(d, result);
  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Unpack_float(adlb_float_t *d, const void *data, size_t length)
{
  ADLB_UNPACK_SCALAR(d, data, length);
  return ADLB_DATA_SUCCESS;
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
ADLB_Unpack_string(adlb_string_t *s, void *data, size_t length, bool copy)
{
  // Must be null-terminated
  if (length < 1 || ((char*)data)[length-1] != '\0')
    return ADLB_DATA_ERROR_INVALID;

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
ADLB_Unpack_blob(adlb_blob_t *b, void *data, size_t length, bool copy)
{
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

/*
  Multiset is packed with a header, then a series of entries.
  Each entry is a key and value packed with ADLB_Append_buffer.
 */
adlb_data_code
ADLB_Pack_container(const adlb_container *container,
          const adlb_buffer *tmp_buf, adlb_buffer *output,
          bool *output_caller_buffer, size_t *output_pos);

adlb_data_code
ADLB_Pack_container_hdr(int elems, adlb_data_type key_type,
        adlb_data_type val_type, adlb_buffer *output,
        bool *output_caller_buffer, size_t *output_pos);

/*
 init_cont: if true, initialize new container
            if not, insert into existing container
 */
adlb_data_code
ADLB_Unpack_container(adlb_container *container,
    const void *data, size_t length, adlb_refc refcounts,
    bool init_cont);

adlb_data_code
ADLB_Unpack_container_hdr(const void *data, size_t length, size_t *pos,
        int *entries, adlb_data_type *key_type, adlb_data_type *val_type);

adlb_data_code
ADLB_Unpack_container_entry(adlb_data_type key_type,
          adlb_data_type val_type, const void *data, size_t length,
          size_t *pos, const void **key, size_t *key_len,
          const void **val, size_t *val_len);

/*
  Multiset is packed with a header, then a series of
  entries.  Each entry is packed with ADLB_Append_buffer.
 */
adlb_data_code
ADLB_Pack_multiset(const adlb_multiset_ptr ms,
          const adlb_buffer *tmp_buf, adlb_buffer *output,
          bool *output_caller_buffer, size_t *output_pos);

adlb_data_code
ADLB_Pack_multiset_hdr(int elems, adlb_data_type elem_type,
    adlb_buffer *output, bool *output_caller_buffer, size_t *output_pos);

adlb_data_code
ADLB_Unpack_multiset(adlb_multiset_ptr *ms, const void *data,
        size_t length, adlb_refc refcounts, bool init_ms);

adlb_data_code
ADLB_Unpack_multiset_hdr(const void *data, size_t length, size_t *pos,
                int *entries, adlb_data_type *elem_type);

adlb_data_code
ADLB_Unpack_multiset_entry(adlb_data_type elem_type,
          const void *data, size_t length, size_t *pos,
          const void **elem, size_t *elem_len);

// Header used for metadata about serialization
// The remainder of buffer has struct fields stored in order
typedef struct {
  adlb_struct_type type;
  // Offsets of fields within buffer relative to start of buffer
  size_t field_offsets[];
} adlb_packed_struct_hdr;

adlb_data_code
ADLB_Pack_struct(const adlb_struct *s, const adlb_buffer *caller_buffer,
                 adlb_binary_data *result);

adlb_data_code
ADLB_Unpack_struct(adlb_struct **s, const void *data, size_t length,
                   adlb_refc refcounts, bool init_struct);

// Free any memory used
adlb_data_code
ADLB_Free_storage(adlb_datum_storage *d, adlb_data_type type);

/**
 * Parse 64-bit integer from fixed-length string
 */
adlb_data_code
ADLB_Int64_parse(const char *str, size_t length, int64_t *result);

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
                             bool *using_caller_buf, size_t init_length)
{
  assert(curr_buffer != NULL);
  bool buf_provided = caller_buffer != NULL;
  assert(!buf_provided || caller_buffer->length >= 0);

  if (!buf_provided || caller_buffer->length < init_length)
  {
    curr_buffer->data = malloc((size_t)init_length);
    curr_buffer->length = init_length;
    if (using_caller_buf != NULL)
      *using_caller_buf = false;
  }
  else
  {
    *curr_buffer = *caller_buffer;
    if (using_caller_buf != NULL)
      *using_caller_buf = true;
  }

  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code
ADLB_Resize_buf(adlb_buffer *buf, bool *using_caller_buf, size_t min_length)
{
  size_t old_length = buf->length;
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
    if (buf->length == 0 || (using_caller_buf != NULL && *using_caller_buf))
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
ADLB_Free_buf(adlb_buffer *buf, bool using_caller_buf)
{
  if (!using_caller_buf && buf->data != NULL)
  {
    free(buf->data);
    buf->data = NULL;
    buf->length = 0;
  }
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

// Free only if pointer owned, and doesn't match
// provided pointer
static inline void
ADLB_Free_binary_data2(adlb_binary_data *buffer,
                       const void *owned)
{
  // Must free any memory allocated
  if (buffer->caller_data != NULL &&
      buffer->caller_data != owned)
  {
    free(buffer->caller_data);
  }
}

__attribute__((always_inline))
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
