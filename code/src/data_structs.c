
#define _GNU_SOURCE // for asprintf()

#include "data_structs.h"

#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>

#include "adlb-defs.h"
#include "data.h"
#include "debug.h"
#include "data_cleanup.h"
#include "data_internal.h"
#include "refcount.h"

typedef struct {
  bool initialized;
  char *type_name;
  int field_count;
  char **field_names;
  adlb_data_type *field_types;
} struct_type_info;

// Dynamically allocated array of struct type entries
static int struct_types_size = 0;
static struct_type_info *struct_types = NULL;

#define is_valid_type(type) (type >= 0 && type < struct_types_size && \
                             struct_types[type].initialized)

#define check_valid_type(type) { \
    check_verbose(is_valid_type(type), ADLB_DATA_ERROR_INVALID, \
            "Invalid type id %i", type); \
  }

static adlb_data_code struct_type_free(struct_type_info *t);
static adlb_struct *alloc_struct(struct_type_info *t);


const char *xlb_struct_type_name(adlb_struct_type type)
{
  if (type >= 0 && type < struct_types_size)
  {
    struct_type_info *info = &struct_types[type];
    if (info->initialized)
    {
      return info->type_name;
    }
  };

  return NULL;
}


static inline adlb_data_code resize_struct_types(adlb_struct_type new_type)
{
  if (new_type >= struct_types_size)
  {
    // Must resize array
    int old_size = struct_types_size;
    struct_types_size *= 2;
    if (new_type >= struct_types_size)
    {
      // Make big enough to fit
      struct_types_size = new_type + 1;
    }
    DATA_REALLOC(struct_types, (uint32_t)struct_types_size);

    for (int i = old_size; i < struct_types_size; i++)
    {
      // Mark newly allocated members as uninitialized
      struct_types[i].initialized = false;
    }
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code ADLB_Declare_struct_type(adlb_struct_type type,
                    const char *type_name,
                    int field_count,
                    const adlb_data_type *field_types,
                    const char **field_names)
{
  adlb_data_code dc;
  check_verbose(type >= 0, ADLB_DATA_ERROR_INVALID,
        "Struct type id %i was negative", type);
  assert(field_count >= 0);
  assert(type_name != NULL);
  assert(field_types != NULL);
  assert(field_names != NULL);

  // Check array big enough
  dc = resize_struct_types(type);
  DATA_CHECK(dc);

  struct_type_info *t = &struct_types[type];
  check_verbose(!t->initialized, ADLB_DATA_ERROR_INVALID,
                "struct type %i already initialized", type);

  t->initialized = true;
  t->type_name = strdup(type_name);
  t->field_count = field_count;
  t->field_names = malloc(sizeof(*(t->field_names)) * (size_t)field_count);
  t->field_types = malloc(sizeof(*(t->field_types)) * (size_t)field_count);
  for (int i = 0; i < field_count; i++)
  {
    assert(field_names[i] != NULL);
    t->field_names[i] = strdup(field_names[i]);
    t->field_types[i] = field_types[i];
  }

  DEBUG("Declared struct type %s with id %i and %i fields",
        type_name, type, field_count);
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_struct_finalize(void)
{
  if (struct_types != NULL)
  {
    // Recursively free type info structure
    for (int i = 0; i < struct_types_size; i++)
    {
      adlb_data_code dc = struct_type_free(&struct_types[i]);
      DATA_CHECK(dc);
    }
    free(struct_types);
    struct_types = NULL;
  }
  struct_types_size = 0;

  return ADLB_DATA_SUCCESS;
}

static
adlb_data_code struct_type_free(struct_type_info *t)
{
  if (t->initialized)
  {
    free(t->type_name);
    for (int f = 0; f < t->field_count; f++)
    {
      free(t->field_names[f]);
    }
    free(t->field_types);
    free(t->field_names);
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
ADLB_Lookup_struct_type(adlb_struct_type type,
                  const char **type_name, int *field_count,
                  const adlb_data_type **field_types, char ***field_names)
{
  check_valid_type(type);

  struct_type_info *t = &struct_types[type];
  *type_name = t->type_name;
  *field_count = t->field_count;
  *field_types = t->field_types;
  *field_names = t->field_names;
  return ADLB_DATA_SUCCESS;
}

static adlb_struct *alloc_struct(struct_type_info *t)
{
  adlb_struct *res;
  res = malloc(sizeof(adlb_struct) +
               (size_t)t->field_count * sizeof(res->data[0]));
  return res;
}

adlb_data_code
ADLB_Unpack_struct(adlb_struct **s, const void *data, int length)
{
  assert(s != NULL);
  assert(length >= 0);
  check_verbose(length >= sizeof(adlb_packed_struct_hdr), ADLB_DATA_ERROR_INVALID,
                "buffer too small for serialized struct");
  const adlb_packed_struct_hdr *hdr = data;
  check_valid_type(hdr->type);
  struct_type_info *t = &struct_types[hdr->type];
  check_verbose((size_t)length >= sizeof(*s) +
                sizeof(*(hdr->field_offsets)) * (size_t)t->field_count,
                ADLB_DATA_ERROR_INVALID,
                "buffer too small for header of struct type %s", t->type_name);

  *s = alloc_struct(t);
  (*s)->type = hdr->type;
  check_verbose(*s != NULL, ADLB_DATA_ERROR_OOM, "Couldn't allocate struct");

  // Go through and assign all of the datums from the data in the buffer
  for (int i = 0; i < t->field_count; i++)
  {
    int offset = hdr->field_offsets[i];
    const void *field_start = data + offset;
    int field_len;
    if (i == t->field_count - 1)
    {
      // Remainder of buffer
      field_len = length - offset;
    }
    else
    {
      field_len = hdr->field_offsets[i + 1] - offset;
    }
    ADLB_Unpack(&(*s)->data[i], t->field_types[i], field_start, field_len);
  }
  return ADLB_DATA_SUCCESS;
}


// Serialize struct into buffer.  Supports user passing in own fixed-size
// buffer that is used if big enough.
adlb_data_code ADLB_Pack_struct(const adlb_struct *s,
             const adlb_buffer *caller_buffer,
             adlb_binary_data *result)
{
  assert(s != NULL);
  adlb_data_code dc;

  check_valid_type(s->type);
  struct_type_info *t = &struct_types[s->type];

  adlb_buffer result_buf;
  // Use double pointer so that *hdr always points to result_buf->data
  adlb_packed_struct_hdr **hdr = (adlb_packed_struct_hdr **) &result_buf.data;

  // Resize buf for header if needed
  int hdr_size = (int)sizeof(**hdr) +
              t->field_count * (int)sizeof((*hdr)->field_offsets[0]);

  bool using_caller_buf;
  dc = ADLB_Init_buf(caller_buffer, &result_buf, &using_caller_buf, hdr_size);
  DATA_CHECK(dc);

  // Add header info
  (*hdr)->type = s->type;
  int buf_pos = hdr_size; // Current amount of buffer used

  for (int i = 0; i < t->field_count; i++)
  {
    adlb_data_type field_t = t->field_types[i];
    adlb_binary_data field_data;

    dc = ADLB_Pack(&s->data[i], field_t, NULL, &field_data);
    DATA_CHECK(dc);
    assert(field_data.data != NULL);
    assert(field_data.length >= 0);

    dc = ADLB_Resize_buf(&result_buf, &using_caller_buf,
                         buf_pos + field_data.length);
    DATA_CHECK(dc);

    // Copy serialized data into buffer
    memcpy((result_buf.data) + buf_pos, field_data.data,
           (size_t)field_data.length);
    // Mark start of data
    (*hdr)->field_offsets[i] = buf_pos;
    buf_pos += field_data.length;

    ADLB_Free_binary_data(&field_data);
  }

  // Fill in result
  result->length = buf_pos;
  // Caller must now take care of allocated data
  result->data = result->caller_data = result_buf.data;
  return ADLB_DATA_SUCCESS;
}

// Get data for struct field
adlb_data_code xlb_struct_get_field(adlb_struct *s, int field_ix,
                        const adlb_datum_storage **val, adlb_data_type *type)
{
  check_valid_type(s->type);
  struct_type_info *st = &struct_types[s->type];
  check_verbose(field_ix >= 0 && field_ix < st->field_count,
                 ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND,
                 "Looking up field #%i in struct type %s with %i fields",
                 field_ix, st->type_name, st->field_count);
  *val = &s->data[field_ix];
  *type = st->field_types[field_ix];

  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_free_struct(adlb_struct *s, bool free_root_ptr)
{
  assert(s != NULL);
  check_valid_type(s->type);

  struct_type_info *t = &struct_types[s->type];
  for (int i = 0; i < t->field_count; i++)
  {
    adlb_data_code dc = ADLB_Free_storage(&s->data[i], t->field_types[i]);
    DATA_CHECK(dc);
  }
  if (free_root_ptr)
    free(s);
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_struct_cleanup(adlb_struct *s, bool free_mem, bool release_read,
                   bool release_write, 
                   adlb_refcounts to_acquire, int acquire_ix,
                   xlb_rc_changes *rc_changes)
{
  assert(s != NULL);
  check_valid_type(s->type);
  adlb_data_code dc;
  struct_type_info *t = &struct_types[s->type];
  assert(acquire_ix < t->field_count);
  bool acquiring = to_acquire.read_refcount != 0 ||
                     to_acquire.write_refcount != 0;
  
  for (int i = 0; i < t->field_count; i++)
  {
    bool acquire_field = acquiring &&
                         (acquire_ix < 0 || acquire_ix == i);
    dc = xlb_incr_referand(&s->data[i], t->field_types[i], release_read,
            release_write, (acquire_field ? to_acquire : ADLB_NO_RC),
            rc_changes);
    DATA_CHECK(dc);
  }

  if (free_mem)
  {
    dc = xlb_free_struct(s, true);
    DATA_CHECK(dc);
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_struct_str_to_ix(adlb_subscript subscript, int *field_ix)
{
  // TODO: use binary repr for subscript?
  char *end;
  // TODO: unsafe, assuming null terminated
  long field_ixl = strtol(subscript.key, &end, 10);
  // TODO: support binary subscript
  check_verbose(end != subscript.key && *end == '\0', ADLB_DATA_ERROR_INVALID,
                "Expected integer subscript for struct: [%.*s]",
                (int)subscript.length, (const char*)subscript.key);
  check_verbose(field_ixl >= 0 && field_ixl < INT_MAX,
                ADLB_DATA_ERROR_INVALID, "Integer subscript for struct"
                " out of range: [%li]", field_ixl);
  *field_ix = (int)field_ixl;
  return ADLB_DATA_SUCCESS;
}

char *xlb_struct_repr(adlb_struct *s)
{
  if (is_valid_type(s->type))
  {
    struct_type_info *t = &struct_types[s->type];
    int total_len = 0;
    char *field_reprs[t->field_count];
    total_len += (int)strlen(t->type_name) + 5;
    for (int i = 0; i < t->field_count; i++)
    {
      field_reprs[i] = ADLB_Data_repr(&s->data[i], t->field_types[i]);
      total_len += (int)strlen(t->field_names[i]);
      total_len += (int)strlen(field_reprs[i]);
      total_len += 7; // Extra chars
    }

    char *result = malloc((size_t)total_len + 1);
    char *pos = result;
    pos += sprintf(pos, "%s: {", t->type_name);
    for (int i = 0; i < t->field_count; i++)
    {
      pos += sprintf(pos, " {%s}={%s},", t->field_names[i], field_reprs[i]);
      free(field_reprs[i]);
    }
    sprintf(pos, " }");
    return result;
  }
  else
  {
    char *result;
    int n = asprintf(&result, "<bad struct type %i>", s->type);
    assert(n != -1);
    return result;
  }
}
