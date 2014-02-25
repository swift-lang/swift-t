
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
  adlb_type_extra extra = { .valid = true, .STRUCT.struct_type=type };
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

adlb_data_code
ADLB_Lookup_struct_type(adlb_struct_type type,
                  const char **type_name, int *field_count,
                  const adlb_struct_field_type **field_types,
                  char ***field_names)
{
  check_valid_type(type);

  xlb_struct_type_info *t = &struct_types[type];
  *type_name = t->type_name;
  *field_count = t->field_count;
  *field_types = t->field_types;
  *field_names = t->field_names;
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
ADLB_Unpack_struct(adlb_struct **s, const void *data, int length,
                   bool init_struct)
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
    int init_offset = hdr->field_offsets[i];
    int data_offset = init_offset + 1;
    
    bool field_init = (((char*)data)[init_offset] != 0);

    if (field_init)
    {
      const void *field_start = data + data_offset;
      int field_len;
      if (i == t->field_count - 1)
      {
        // Remainder of buffer
        field_len = length - data_offset;
      }
      else
      {
        field_len = hdr->field_offsets[i + 1] - data_offset;
      }
      if (!init_struct && (*s)->fields[i].initialized)
      {
        // Free any existing data
        dc = ADLB_Free_storage(&(*s)->fields[i].data,
                               t->field_types[i].type);
        DATA_CHECK(dc);
      }

      ADLB_Unpack(&(*s)->fields[i].data, t->field_types[i].type,
                  field_start, field_len);
    }
    (*s)->fields[i].initialized = field_init;
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

  int ix;
  dc = xlb_struct_str_to_ix(subscript, &ix);
  DATA_CHECK(dc);

  return xlb_struct_get_field(s, ix, val, type);
}

adlb_data_code xlb_struct_subscript_init(adlb_struct *s, adlb_subscript subscript,
                                        bool *b)
{
  adlb_data_code dc;

  int ix;
  dc = xlb_struct_str_to_ix(subscript, &ix);
  DATA_CHECK(dc);


  adlb_struct_field *f;
  xlb_struct_type_info *st;
  dc = get_field(s, ix, &st, &f);
  DATA_CHECK(dc);

  *b = f->initialized;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_struct_set_field(adlb_struct *s, int field_ix,
                        const void *data, int length, adlb_data_type type)
{
  adlb_struct_field *f;
  xlb_struct_type_info *st;
  adlb_data_code dc = get_field(s, field_ix, &st, &f);
  DATA_CHECK(dc);

  check_verbose(!f->initialized, ADLB_DATA_ERROR_DOUBLE_WRITE,
        "Field %s of struct type %s already set",
        st->field_names[field_ix], st->type_name);
  check_verbose(st->field_types[field_ix].type == type, ADLB_DATA_ERROR_TYPE,
        "Invalid type %s when assigning to field %s: expected %s",
        ADLB_Data_type_tostring(type), st->field_names[field_ix],
        ADLB_Data_type_tostring(st->field_types[field_ix].type));

  // TODO: recursively traverse nested structs to do assign?

  dc = ADLB_Unpack(&f->data, type, data, length); 
  DATA_CHECK(dc);
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_struct_set_subscript(adlb_struct *s, adlb_subscript subscript,
                        const void *data, int length, adlb_data_type type)
{
  adlb_data_code dc;

  int ix;
  dc = xlb_struct_str_to_ix(subscript, &ix);
  DATA_CHECK(dc);

  return xlb_struct_set_field(s, ix, data, length, type);
}

adlb_data_code xlb_free_struct(adlb_struct *s, bool free_root_ptr)
{
  adlb_data_code dc;

  assert(s != NULL);
  check_valid_type(s->type);

  xlb_struct_type_info *t = &struct_types[s->type];
  for (int i = 0; i < t->field_count; i++)
  {
    if (s->fields[i].initialized)
    {
      dc = ADLB_Free_storage(&s->fields[i].data, t->field_types[i].type);
      DATA_CHECK(dc);
    }
  }
  if (free_root_ptr)
    free(s);
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_struct_cleanup(adlb_struct *s, bool free_mem, bool release_read,
                   bool release_write, 
                   xlb_acquire_rc to_acquire, xlb_rc_changes *rc_changes)
{
  assert(s != NULL);
  check_valid_type(s->type);
  adlb_data_code dc;
  xlb_struct_type_info *t = &struct_types[s->type];
  bool acquiring = !ADLB_RC_IS_NULL(to_acquire.refcounts);
  
  int acquire_ix = -1; // negative == acquire all subscripts
  if (adlb_has_sub(to_acquire.subscript)) 
  {
    dc = xlb_struct_str_to_ix(to_acquire.subscript, &acquire_ix);
    DATA_CHECK(dc);
    check_verbose(acquire_ix < t->field_count, ADLB_DATA_ERROR_INVALID,
                "Out of range struct index: %i, type %s field count %i",
                acquire_ix, t->type_name, t->field_count);
  }

  for (int i = 0; i < t->field_count; i++)
  {
    if (!s->fields[i].initialized)
      // Skip acquiring/freeing uninitialized fields
      continue;

    bool acquire_field = acquiring &&
                         (acquire_ix < 0 || acquire_ix == i);
    dc = xlb_incr_referand(&s->fields[i].data, t->field_types[i].type,
            release_read, release_write,
            (acquire_field ? to_acquire : XLB_NO_ACQUIRE), rc_changes);
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
  // TODO: use binary repr for subscript? vint?
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
