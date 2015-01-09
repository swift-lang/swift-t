
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

// Dynamically allocated array of struct type entries
static int struct_types_size = 0;
static xlb_struct_type_info *struct_types = NULL;

#define is_valid_type(type) (type >= 0 && type < struct_types_size && \
                             struct_types[type].initialized)

#define check_valid_type(type) { \
    check_verbose(is_valid_type(type), ADLB_DATA_ERROR_INVALID, \
            "Invalid type id %i", type); \
  }

static adlb_data_code struct_type_free(xlb_struct_type_info *t);
static adlb_struct *alloc_struct(xlb_struct_type_info *t);

static adlb_data_code
struct_subscript_pop(adlb_subscript *sub, int *field_ix,
                     size_t *consumed);

const char *xlb_struct_type_name(adlb_struct_type type)
{
  if (type >= 0 && type < struct_types_size)
  {
    xlb_struct_type_info *info = &struct_types[type];
    if (info->initialized)
    {
      return info->type_name;
    }
  };

  return NULL;
}

const xlb_struct_type_info *
xlb_get_struct_type_info(adlb_struct_type type)
{
  if (is_valid_type(type))
    return &struct_types[type];
  else
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
                    const adlb_struct_field_type *field_types,
                    const char **field_names)
{
  adlb_data_code dc;
  check_verbose(type >= 0, ADLB_DATA_ERROR_INVALID,
        "Struct type id %i was negative", type);
  assert(field_count >= 0);
  assert(type_name != NULL);
  assert(field_types != NULL);
  assert(field_names != NULL);

  adlb_data_type tmp_type;
  adlb_type_extra tmp_extra;
  dc = xlb_data_type_lookup(type_name, &tmp_type, &tmp_extra);
  DATA_CHECK(dc);

  check_verbose(tmp_type == ADLB_DATA_TYPE_NULL, ADLB_DATA_ERROR_TYPE,
            "Type called %s already exists", type_name);

  // Check array big enough
  dc = resize_struct_types(type);
  DATA_CHECK(dc);

  xlb_struct_type_info *t = &struct_types[type];
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

  // Add struct type name to index
  adlb_type_extra extra;
  extra.valid = true;
  extra.STRUCT.struct_type = type;

  dc = xlb_data_type_add(type_name, ADLB_DATA_TYPE_STRUCT, extra);
  DATA_CHECK(dc);

  // Also add alias name, e.g. struct1, for backward compatibility
  char *tmp_type_name;
  int n = asprintf(&tmp_type_name, "struct%i", type);
  check_verbose(n != -1, ADLB_DATA_ERROR_OOM, "Error printing string");
  dc = xlb_data_type_add(tmp_type_name, ADLB_DATA_TYPE_STRUCT, extra);
  free(tmp_type_name);

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
adlb_data_code struct_type_free(xlb_struct_type_info *t)
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

/**
 * Extract first component of struct subscript, and
 * update subscript to refer to remainder, or ADLB_NO_SUB if finished.
 * consumed: number of bytes of subscript consumed
 */
static adlb_data_code
struct_subscript_pop(adlb_subscript *sub, int *field_ix,
                     size_t *consumed)
{
  adlb_data_code dc;
  // Struct subscripts are of form <integer>(.<integer>)*'\0'?

  const char *sub_str = sub->key;
  // Locate next '.', if any
  void *sep = memchr(sub_str, '.', sub->length);
  size_t component_len;
  if (sep == NULL)
  {
    component_len = sub->length;
    // May or may not be null-terminated
    if (sub_str[component_len - 1] == '\0')
    {
      component_len--;
    }

    *consumed = sub->length;

    // Finished with subscript
    *sub = ADLB_NO_SUB;
  }
  else
  {
    component_len = (size_t)((char*)sep - sub_str);
    sub->key = ((const char*)sep) + 1;
    *consumed = component_len + 1; // Including separator
    assert(*consumed <= sub->length);
    sub->length = sub->length - *consumed;
  }

  int64_t field_ix64;
  dc = ADLB_Int64_parse(sub_str, component_len, &field_ix64);
  check_verbose(dc == ADLB_DATA_SUCCESS, ADLB_DATA_ERROR_INVALID,
        "Invalid subscript component: \"%.*s\" len %zu",
        (int)component_len, sub_str, component_len);

  check_verbose(field_ix64 >= 0 && field_ix64 <= INT_MAX,
      ADLB_DATA_ERROR_INVALID, "Struct index out of range: %"PRId64,
      field_ix64);

  *field_ix = (int)field_ix64;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
ADLB_Lookup_struct_type(adlb_struct_type type,
                  const char **type_name, int *field_count,
                  const adlb_struct_field_type **field_types,
                  char const* const** field_names)
{
  check_valid_type(type);

  xlb_struct_type_info *t = &struct_types[type];
  if (type_name != NULL)
    *type_name = t->type_name;

  if (field_count != NULL)
    *field_count = t->field_count;

  if (field_types != NULL)
    *field_types = t->field_types;

  if (field_names != NULL)
  {
    // Convert to non-modifiable pointer
    char const* const* tmp_field_names = 
                    (char const* const*)t->field_names;
    *field_names = tmp_field_names;
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_new_struct(adlb_struct_type type, adlb_struct **s)
{
  check_valid_type(type);

  xlb_struct_type_info *t = &struct_types[type];

  adlb_struct *tmp = alloc_struct(t);
  check_verbose(tmp != NULL, ADLB_DATA_ERROR_OOM, "Out of memory");

  tmp->type = type;
  for (int i = 0; i < t->field_count; i++)
  {
    tmp->fields[i].initialized = false;
  }
  *s = tmp;

  return ADLB_DATA_SUCCESS;
}

static adlb_struct *alloc_struct(xlb_struct_type_info *t)
{
  adlb_struct *res;
  res = malloc(sizeof(adlb_struct) +
               (size_t)t->field_count * sizeof(res->fields[0]));
  return res;
}

adlb_data_code
ADLB_Unpack_struct(adlb_struct **s, const void *data, size_t length,
                   adlb_refc refcounts, bool init_struct)
{
  adlb_data_code dc;

  assert(s != NULL);
  assert(length >= 0);
  check_verbose(length >= sizeof(adlb_packed_struct_hdr), ADLB_DATA_ERROR_INVALID,
                "buffer too small for serialized struct");
  const adlb_packed_struct_hdr *hdr = data;
  check_valid_type(hdr->type);
  xlb_struct_type_info *t = &struct_types[hdr->type];
  check_verbose((size_t)length >= sizeof(*s) +
                sizeof(*(hdr->field_offsets)) * (size_t)t->field_count,
                ADLB_DATA_ERROR_INVALID,
                "buffer too small for header of struct type %s", t->type_name);

  if (init_struct)
  {
    *s = alloc_struct(t);
    check_verbose(*s != NULL, ADLB_DATA_ERROR_OOM, "Couldn't allocate struct");
    (*s)->type = hdr->type;

    for (int i = 0; i < t->field_count; i++)
    {
      // Need to mark fields as uninitialized
      (*s)->fields[i].initialized = false;
    }
  }
  else
  {
    assert(is_valid_type((*s)->type));
    check_verbose((*s)->type == hdr->type, ADLB_DATA_ERROR_TYPE,
             "Type of target struct doesn't match source data: %s vs. %s",
              struct_types[(*s)->type].type_name, t->type_name); 
  }

  // Go through and assign all of the datums from the data in the buffer
  for (int i = 0; i < t->field_count; i++)
  {
    size_t init_offset = hdr->field_offsets[i];
    size_t data_offset = init_offset + 1;

    bool field_init = (((char*)data)[init_offset] != 0);

    if (field_init)
    {
      const void *field_start = ((const char*)data) + data_offset;
      size_t field_len;
      if (i == t->field_count - 1)
      {
        // Remainder of buffer
        field_len = length - data_offset;
      }
      else
      {
        field_len = hdr->field_offsets[i + 1] - data_offset;
      }

      if ((*s)->fields[i].initialized)
      {
        // Free any existing data
        dc = ADLB_Free_storage(&(*s)->fields[i].data,
                               t->field_types[i].type);
        DATA_CHECK(dc);
      }

      ADLB_Unpack(&(*s)->fields[i].data, t->field_types[i].type,
                  field_start, field_len, true, refcounts);
      (*s)->fields[i].initialized = true;
    }
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
  xlb_struct_type_info *t = &struct_types[s->type];

  adlb_buffer result_buf;
  // Use double pointer so that *hdr always points to result_buf->data
  adlb_packed_struct_hdr **hdr = (adlb_packed_struct_hdr **) &result_buf.data;

  // Resize buf for header if needed
  size_t hdr_size = sizeof(**hdr) +
      ((size_t) t->field_count) * sizeof((*hdr)->field_offsets[0]);

  bool using_caller_buf;
  dc = ADLB_Init_buf(caller_buffer, &result_buf, &using_caller_buf, hdr_size);
  DATA_CHECK(dc);

  // Add header info
  (*hdr)->type = s->type;
  size_t buf_pos = hdr_size; // Current amount of buffer used

  for (int i = 0; i < t->field_count; i++)
  {
    adlb_data_type field_t = t->field_types[i].type;
    adlb_binary_data field_data;

    bool init = s->fields[i].initialized;
    if (init)
    {
      dc = ADLB_Pack(&s->fields[i].data, field_t, NULL, &field_data);
      DATA_CHECK(dc);
      assert(field_data.data != NULL);
      assert(field_data.length >= 0);
    }
    else
    {
      field_data.length = 0;
    }

    dc = ADLB_Resize_buf(&result_buf, &using_caller_buf,
                         buf_pos + field_data.length + 1);
    DATA_CHECK(dc);

    // Mark start of data
    (*hdr)->field_offsets[i] = buf_pos;

    // Mark whether present or not
    result_buf.data[buf_pos++] = init;

    if (init)
    {
      // Copy serialized data into buffer
      memcpy((result_buf.data) + buf_pos, field_data.data,
             (size_t)field_data.length);
      buf_pos += field_data.length;

      ADLB_Free_binary_data(&field_data);
    }
  }

  // Fill in result
  result->length = buf_pos;
  // Caller must now take care of allocated data
  result->data = result->caller_data = result_buf.data;
  return ADLB_DATA_SUCCESS;
}

static adlb_data_code get_field(adlb_struct *s, int field_ix,
            xlb_struct_type_info **st, adlb_struct_field **f)
{
  check_valid_type(s->type);
  *st = &struct_types[s->type];
  check_verbose(field_ix >= 0 && field_ix < (*st)->field_count,
                 ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND,
                 "Looking up field #%i in struct type %s with %i fields",
                 field_ix, (*st)->type_name, (*st)->field_count);
  *f = &s->fields[field_ix];
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_struct_lookup(adlb_struct *s, adlb_subscript sub, bool init_nested,
                    adlb_struct_field **field, adlb_struct_field_type *type,
                    size_t *sub_pos)
{
  assert(sub.key != NULL);
  assert(sub.length > 0);

  adlb_data_code dc;
  size_t pos = 0; // position relative to start

  while (true)
  {
    assert(sub.length > 0);
    int field_ix;
    size_t consumed;

    dc = struct_subscript_pop(&sub, &field_ix, &consumed);
    DATA_CHECK(dc);

    pos += consumed;

    adlb_struct_field *curr_field;
    xlb_struct_type_info *st;
    dc = get_field(s, field_ix, &st, &curr_field);
    DATA_CHECK(dc);
    TRACE("Pop sub %i", field_ix);
    adlb_struct_field_type *field_type = &st->field_types[field_ix];

    if (!adlb_has_sub(sub))
    {
      *field = curr_field;
      *type = *field_type;
      *sub_pos = pos;
      return ADLB_DATA_SUCCESS;
    }
    else if (field_type->type == ADLB_DATA_TYPE_STRUCT &&
             (curr_field->initialized || init_nested))
    {
      if (!curr_field->initialized)
      {
        dc = xlb_new_struct(field_type->extra.STRUCT.struct_type,
                            &curr_field->data.STRUCT);
        DATA_CHECK(dc);
        curr_field->initialized = true;
      }
      // Another iteration if it's a valid struct
      s = curr_field->data.STRUCT;
    }
    else
    {
      // Not a struct: return
      *field = curr_field;
      *type = *field_type;
      *sub_pos = pos;
      return ADLB_DATA_SUCCESS;
    }
  }
  return ADLB_DATA_SUCCESS;
}

// Get data for struct field
adlb_data_code xlb_struct_get_field(adlb_struct *s, int field_ix,
                        const adlb_datum_storage **val, adlb_data_type *type)
{
  adlb_struct_field *f;
  xlb_struct_type_info *st;
  adlb_data_code dc = get_field(s, field_ix, &st, &f);
  DATA_CHECK(dc);

  *type = st->field_types[field_ix].type;
  DEBUG("Field type: %s", ADLB_Data_type_tostring(*type));
  if (f->initialized)
  {
    *val = &f->data;
  }
  else
  {
    *val = NULL;
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_struct_get_subscript(adlb_struct *s, adlb_subscript subscript,
                        const adlb_datum_storage **val, adlb_data_type *type)
{
  adlb_data_code dc;

  adlb_struct_field *field;
  adlb_struct_field_type field_type;
  size_t sub_pos;
  dc = xlb_struct_lookup(s, subscript, false, &field, &field_type, &sub_pos);
  DATA_CHECK(dc);

  check_verbose(sub_pos == subscript.length,
        ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND,
        "Could not lookup full subscript: remainder was [%.*s]",
        (int)(subscript.length - sub_pos),
        ((const char*)subscript.key) + sub_pos);

  check_verbose(field->initialized, ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND,
        "Subscript [%.*s] not initialized", (int)subscript.length,
        (const char*)subscript.key);

  *val = &field->data;
  *type = field_type.type;

  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_struct_subscript_init(adlb_struct *s, adlb_subscript subscript,
                  bool validate_path, bool *b)
{
  adlb_data_code dc;

  adlb_struct_field *field;
  adlb_struct_field_type field_type;
  size_t sub_pos;

  // Initialize subscripts as a way to validate path
  bool init_nested = validate_path;
  dc = xlb_struct_lookup(s, subscript, init_nested, &field, &field_type, &sub_pos);
  DATA_CHECK(dc);

  if (sub_pos == subscript.length)
  {
    // Entire subscript was consumed
    *b = field->initialized;
  }
  else
  {
    DEBUG("%zu vs %zu", sub_pos, subscript.length);
    if (validate_path)
    {
      verbose_error(ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND,
          "Subscript invalid: [%.*s] (tail of [%.*s])",
          (int)(subscript.length - sub_pos),
          &((const char *)subscript.key)[sub_pos],
          (int)subscript.length, (const char*)subscript.key);
    }
    else
    {
      *b = false;
    }
  }

  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_struct_assign_field(adlb_struct_field *field,
        adlb_struct_field_type field_type, const void *data, size_t length,
        adlb_data_type data_type, adlb_refc refcounts)
{
  adlb_data_code dc;

  // Non-compound fields can only be initialized/assigned once
  check_verbose(ADLB_Data_is_compound(field_type.type) ||
        !field->initialized, ADLB_DATA_ERROR_DOUBLE_WRITE,
        "Field already set");

  check_verbose(field_type.type == data_type, ADLB_DATA_ERROR_TYPE,
        "Invalid type %s when assigning to struct field: expected %s",
        ADLB_Data_type_tostring(field_type.type),
        ADLB_Data_type_tostring(data_type));

  // Assign, initializing compound type if needed
  dc = ADLB_Unpack2(&field->data, data_type, data, length, true, refcounts,
                    !field->initialized); 
  DATA_CHECK(dc);
  field->initialized = true;

  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_struct_set_field(adlb_struct *s, int field_ix,
                const void *data, size_t length, adlb_data_type type,
                adlb_refc refcounts)
{
  adlb_struct_field *f;
  xlb_struct_type_info *st;
  adlb_data_code dc = get_field(s, field_ix, &st, &f);
  DATA_CHECK(dc);

  return xlb_struct_assign_field(f, st->field_types[field_ix],
                                 data, length, type, refcounts);
}

adlb_data_code xlb_struct_set_subscript(adlb_struct *s,
      adlb_subscript subscript, bool init_nested,
      const void *data, size_t length, adlb_data_type type,
      adlb_refc refcounts)
{
  adlb_data_code dc;

  adlb_struct_field *field;
  adlb_struct_field_type field_type;
  size_t sub_pos;
  dc = xlb_struct_lookup(s, subscript, init_nested, &field,
                         &field_type, &sub_pos);
  DATA_CHECK(dc);

  check_verbose(sub_pos == subscript.length,
        ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND,
        "Could not lookup full subscript: remainder was [%.*s]",
        (int)(subscript.length - sub_pos),
        ((const char*)subscript.key) + sub_pos);

  check_verbose(field->initialized, ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND,
        "Subscript [%.*s] not initialized", (int)subscript.length,
        (const char*)subscript.key);

  return xlb_struct_assign_field(field, field_type, data, length, type,
                                 refcounts);
}

adlb_data_code xlb_free_struct(adlb_struct *s, bool free_root_ptr,
                               bool recurse)
{
  adlb_data_code dc;

  assert(s != NULL);
  check_valid_type(s->type);

  if (recurse)
  {
    xlb_struct_type_info *t = &struct_types[s->type];
    for (int i = 0; i < t->field_count; i++)
    {
      if (s->fields[i].initialized)
      {
        dc = ADLB_Free_storage(&s->fields[i].data, t->field_types[i].type);
        DATA_CHECK(dc);
      }
    }
  }

  if (free_root_ptr)
  {
    free(s);
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_struct_cleanup(adlb_struct *s, bool free_mem, bool release_read,
                   bool release_write, 
                   xlb_refc_acquire to_acquire, xlb_refc_changes *refcs)
{
  assert(s != NULL);
  check_valid_type(s->type);
  adlb_data_code dc;
  xlb_struct_type_info *t = &struct_types[s->type];
  bool acquiring = !ADLB_REFC_IS_NULL(to_acquire.refcounts);

  int acquire_ix = -1; // negative means acquire all subscripts
  if (adlb_has_sub(to_acquire.subscript)) 
  {
    // separate initial component from rest
    // this leaves trailing subscript in to_acquire, allowing us
    // to pass it directly to recursive calls
    DEBUG("xlb_struct_cleanup sub before: [%.*s]",
          (int)to_acquire.subscript.length,
          (const char*)to_acquire.subscript.key);
    size_t consumed;
    dc = struct_subscript_pop(&to_acquire.subscript, &acquire_ix,
                              &consumed);
    DATA_CHECK(dc);
    DEBUG("xlb_struct_cleanup sub after: [%.*s]",
          (int)to_acquire.subscript.length,
          (const char*)to_acquire.subscript.key);

    check_verbose(acquire_ix < t->field_count, ADLB_DATA_ERROR_INVALID,
                "Out of range struct index: %i, type %s field count %i",
                acquire_ix, t->type_name, t->field_count);
  }

  for (int i = 0; i < t->field_count; i++)
  {
    if (!s->fields[i].initialized)
    {
      check_verbose(i != acquire_ix, ADLB_DATA_ERROR_SUBSCRIPT_NOT_FOUND,
          "Could not acquire subscript [%.*s]",
          (int)to_acquire.subscript.length,
          (const char*)to_acquire.subscript.key);
      // Skip acquiring/freeing uninitialized fields
      continue;
    }

    adlb_data_type field_type = t->field_types[i].type;
    adlb_datum_storage *field_data = &s->fields[i].data;

    bool acquiring_field = acquiring &&
                         (acquire_ix < 0 || acquire_ix == i);
    xlb_refc_acquire acquire_field = acquiring_field ? to_acquire
                                                   : XLB_NO_ACQUIRE;

    switch (field_type)
    {
      case ADLB_DATA_TYPE_STRUCT:
        // Call directly so fewer recursive calls for nested structs
        dc = xlb_struct_cleanup(field_data->STRUCT, free_mem,
              release_read, release_write, acquire_field, refcs);
        DATA_CHECK(dc);
        break;
      default:
        dc = xlb_datum_cleanup(field_data, field_type, free_mem,
                release_read, release_write, acquire_field, refcs);
        DATA_CHECK(dc);
        break;
    }
  }

  if (free_mem)
  {
    dc = xlb_free_struct(s, true, false);
    DATA_CHECK(dc);
  }
  return ADLB_DATA_SUCCESS;
}

char *xlb_struct_repr(adlb_struct *s)
{
  if (is_valid_type(s->type))
  {
    xlb_struct_type_info *t = &struct_types[s->type];
    int total_len = 0;
    char *field_reprs[t->field_count];
    total_len += (int)strlen(t->type_name) + 5;
    for (int i = 0; i < t->field_count; i++)
    {
      if (s->fields[i].initialized)
      {
        field_reprs[i] = ADLB_Data_repr(&s->fields[i].data,
                                        t->field_types[i].type);
        total_len += (int)strlen(field_reprs[i]);
      }
      else
      {
        field_reprs[i] = NULL;
        total_len += 4; // "NULL"
      }
      total_len += (int)strlen(t->field_names[i]);
      total_len += 7; // Extra chars
    }

    char *result = malloc((size_t)total_len + 1);
    char *pos = result;
    pos += sprintf(pos, "%s: {", t->type_name);
    for (int i = 0; i < t->field_count; i++)
    {
      if (field_reprs[i] != NULL)
      {
        pos += sprintf(pos, " {%s}={%s},", t->field_names[i], field_reprs[i]);
        free(field_reprs[i]);
      }
      else
      {
        pos += sprintf(pos, " {%s}=NULL,", t->field_names[i]);
      }
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
