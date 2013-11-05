
#define _GNU_SOURCE // for asprintf()

#include "adlb_types.h"

#include <stdint.h>
#include <inttypes.h>
#include <stdio.h>

#include <vint.h>

#include "adlb.h"
#include "checks.h"
#include "data_cleanup.h"
#include "data_internal.h"
#include "data_structs.h"
#include "multiset.h"

static char *data_repr_container(const adlb_container *c);

adlb_data_code
ADLB_Pack(const adlb_datum_storage *d, adlb_data_type type,
          const adlb_buffer *caller_buffer,
          adlb_binary_data *result)
{
  adlb_data_code dc;
  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      return ADLB_Pack_integer(&d->INTEGER, result);
    case ADLB_DATA_TYPE_REF:
      return ADLB_Pack_ref(&d->REF, result);
    case ADLB_DATA_TYPE_FLOAT:
      return ADLB_Pack_float(&d->FLOAT, result);
    case ADLB_DATA_TYPE_STRING:
      return ADLB_Pack_string(&d->STRING, result);
    case ADLB_DATA_TYPE_BLOB:
      return ADLB_Pack_blob(&d->BLOB, result);
    case ADLB_DATA_TYPE_FILE_REF:
      return ADLB_Pack_file_ref(&d->FILE_REF, result);
    case ADLB_DATA_TYPE_STRUCT:
      return ADLB_Pack_struct(d->STRUCT, caller_buffer, result);
    case ADLB_DATA_TYPE_CONTAINER:
    case ADLB_DATA_TYPE_MULTISET: {
      // Use ADLB_Pack_buffer implementation for these compound types
      // since we need to accumulate data at the end of the buffer
      adlb_buffer res;
      bool use_caller_buf;
      if (caller_buffer == NULL)
      {
        res.data = NULL;
        res.length = 0;
        use_caller_buf = false;
      }
      else
      {
        res = *caller_buffer;
        use_caller_buf = true;
      }
      int pos = 0;
      dc = ADLB_Pack_buffer(d, type, NULL, &res, &use_caller_buf, &pos);
      DATA_CHECK(dc);
      result->data = result->caller_data = res.data;
      result->length = res.length;
      
      return ADLB_DATA_SUCCESS; 
    }
    default:
      verbose_error(ADLB_DATA_ERROR_TYPE,
        "Cannot serialize unknown type %i!\n", type);
  }
  // Unreachable:
  return ADLB_DATA_ERROR_UNKNOWN;
}

adlb_data_code
ADLB_Pack_buffer(const adlb_datum_storage *d, adlb_data_type type,
        const adlb_buffer *tmp_buf, adlb_buffer *output,
        bool *output_caller_buffer, int *output_pos)
{
  adlb_data_code dc;

  // Some types are implemented by appending to buffer anyway
  if (type == ADLB_DATA_TYPE_CONTAINER ||
      type == ADLB_DATA_TYPE_MULTISET)
  {
    // Reserve space at front to prefix serialized size in bytes
    int required = *output_pos + (int)VINT_MAX_BYTES;
    dc = ADLB_Resize_buf(output, output_caller_buffer, required);
    DATA_CHECK(dc);

    int start_pos = *output_pos;
    memset(output->data + start_pos, 0, VINT_MAX_BYTES);
    *output_pos += VINT_MAX_BYTES;
    if (type == ADLB_DATA_TYPE_CONTAINER)
    {
      dc = ADLB_Pack_container(&d->CONTAINER, tmp_buf, output,
                              output_caller_buffer, output_pos);
      DATA_CHECK(dc);
    }
    else
    {
      assert(type == ADLB_DATA_TYPE_MULTISET);
      dc = ADLB_Pack_multiset(d->MULTISET, tmp_buf, output,
                              output_caller_buffer, output_pos);
      DATA_CHECK(dc);
    }

    // Add in actual size to reserved place
    int serialized_len = *output_pos - start_pos;
    vint_encode(serialized_len, output->data + start_pos);

    return ADLB_DATA_SUCCESS;
  }

  // Get binary representation of datum
  adlb_binary_data packed;
  dc = ADLB_Pack(d, type, tmp_buf, &packed); 
  DATA_CHECK(dc);

  // Check buffer large enough for this member
  int required_size = *output_pos + (int)VINT_MAX_BYTES + packed.length;
  dc = ADLB_Resize_buf(output, output_caller_buffer, required_size);
  DATA_CHECK(dc);

  // Prefix with length of member
  int vint_len = vint_encode(packed.length, output->data + *output_pos);
  assert(vint_len >= 1);
  *output_pos += vint_len;

  // Copy in data
  assert(packed.length >= 0);
  memcpy(output->data + *output_pos, packed.data, (size_t)packed.length);
  *output_pos += packed.length;
  
  // Free any malloced temporary memory
  if (tmp_buf != NULL && packed.data != tmp_buf->data)
    ADLB_Free_binary_data(&packed);

  return ADLB_DATA_SUCCESS;
}

adlb_data_code
ADLB_Pack_container(const adlb_container *container,
          const adlb_buffer *tmp_buf, adlb_buffer *output,
          bool *output_caller_buffer, int *output_pos)
{
  int dc;
  int required = *output_pos + (int)VINT_MAX_BYTES;
  dc = ADLB_Resize_buf(output, output_caller_buffer, required);
  DATA_CHECK(dc);

  const struct table_bp *members = container->members;
  int vint_len = vint_encode(members->size, output->data + *output_pos);
  assert(vint_len >= 1);
  *output_pos += vint_len;

  int appended = 0;

  for (int i = 0; i < members->capacity; i++)
  {
    struct list_bp* L = members->array[i];
    struct list_bp_item* item = L->head;
    while (item != NULL)
    {
      // append key; append val
      required = *output_pos + (int)VINT_MAX_BYTES + item->key_len;
      dc = ADLB_Resize_buf(output, output_caller_buffer, required);
      DATA_CHECK(dc);

      int vint_len = vint_encode(item->key_len, output->data + *output_pos);
      assert(vint_len >= 1);
      *output_pos += vint_len;
      memcpy(output->data + *output_pos, item->key, item->key_len);
      *output_pos += item->key_len;

      dc = ADLB_Pack_buffer(item->data, container->val_type,
              tmp_buf, output, output_caller_buffer, output_pos);
      DATA_CHECK(dc);

      appended++;
      item = item->next;
    }
  }
  
  // Check that the number we appended matches
  assert(appended == members->size);
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
ADLB_Pack_multiset(adlb_multiset_ptr ms,
          const adlb_buffer *tmp_buf, adlb_buffer *output,
          bool *output_caller_buffer, int *output_pos)
{
  int dc;
  int required = *output_pos + (int)VINT_MAX_BYTES;
  dc = ADLB_Resize_buf(output, output_caller_buffer, required);
  DATA_CHECK(dc);

  int64_t size = xlb_multiset_size(ms);
  int vint_len = vint_encode(size, output->data + *output_pos);
  assert(vint_len >= 1);
  *output_pos += vint_len;

  int appended = 0;

  for (uint i = 0; i < ms->chunk_count; i++)
  {
    xlb_multiset_chunk *chunk = ms->chunks[i];
    uint chunk_len = (i == ms->chunk_count - 1) ?
          ms->last_chunk_elems : XLB_MULTISET_CHUNK_SIZE;
    
    for (uint j = 0; j < chunk_len; j++)
    {
      // append value
      dc = ADLB_Pack_buffer(&chunk->arr[j], ms->elem_type,
              tmp_buf, output, output_caller_buffer, output_pos);
      DATA_CHECK(dc);

      appended++;
    }
  }

  // Check that the number we appended matches
  assert(appended == size);
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
ADLB_Unpack_buffer(const void *buffer, int length, int *pos,
                   const void **entry, int *entry_length)
{
  assert(buffer != NULL);
  assert(length >= 0);
  assert(pos != NULL);
  assert(*pos >= 0);
  assert(entry != NULL);
  assert(entry_length != NULL);

  if (*pos == length)
  {
    return ADLB_DATA_DONE;
  }
  int64_t entry_length64;
  int vint_len = vint_decode(buffer + *pos, length - *pos,
                             &entry_length64);
  check_verbose(vint_len >= 1, ADLB_DATA_ERROR_INVALID,
      "Error decoding entry length when unpacking buffer");
  check_verbose(entry_length64 >= 0, ADLB_DATA_ERROR_INVALID,
       "Packed buffer entry length < 0");
  check_verbose(entry_length64 <= INT_MAX, ADLB_DATA_ERROR_INVALID,
       "Packed buffer encode entry length too long for int: %"PRId64,
       entry_length64);
  int remaining = length - *pos - vint_len;
  check_verbose(entry_length64 <= remaining,
        ADLB_DATA_ERROR_INVALID, "Decoded entry less than remaining data "
        "in buffer: %d remains, length %"PRId64, remaining, entry_length64);
  *entry_length = (int)entry_length64;
  *entry = buffer + *pos + vint_len;
  *pos += vint_len + *entry_length;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code ADLB_Unpack(adlb_datum_storage *d, adlb_data_type type,
                            const void *buffer, int length)
{
  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      return ADLB_Unpack_integer(&d->INTEGER, buffer, length);
    case ADLB_DATA_TYPE_REF:
      return ADLB_Unpack_ref(&d->REF, buffer, length);
    case ADLB_DATA_TYPE_FLOAT:
      return ADLB_Unpack_float(&d->FLOAT, buffer, length);
    case ADLB_DATA_TYPE_STRING:
      // Ok to cast from const buffer since we force it to copy
      return ADLB_Unpack_string(&d->STRING, (void *)buffer, length, true);
    case ADLB_DATA_TYPE_BLOB:
      // Ok to cast from const buffer since we force it to copy
      return ADLB_Unpack_blob(&d->BLOB, (void *)buffer, length, true);
    case ADLB_DATA_TYPE_FILE_REF:
      return ADLB_Unpack_file_ref(&d->FILE_REF, buffer, length);
    case ADLB_DATA_TYPE_STRUCT:
      return ADLB_Unpack_struct(&d->STRUCT, buffer, length);
    case ADLB_DATA_TYPE_CONTAINER:
    case ADLB_DATA_TYPE_MULTISET:
      // closed- do nothing
      // TODO: unpack these
      printf("Cannot unpack type: %i\n", type);
      return ADLB_DATA_ERROR_INVALID;
      break;
    default:
      printf("data_store(): unknown type: %i\n", type);
      return ADLB_DATA_ERROR_INVALID;
      break;
  }
  return ADLB_DATA_SUCCESS;
}


/* Free the memory associated with datum contents */
adlb_data_code ADLB_Free_storage(adlb_datum_storage *d, adlb_data_type type)
{
  adlb_data_code dc;
  switch (type)
  {
    case ADLB_DATA_TYPE_STRING:
      free(d->STRING.value);
      break;
    case ADLB_DATA_TYPE_BLOB:
      free(d->BLOB.value);
      break;
    case ADLB_DATA_TYPE_CONTAINER:
    {
      dc = xlb_members_cleanup(&d->CONTAINER, true, ADLB_NO_RC, NO_SCAVENGE);
      DATA_CHECK(dc);
      break;
    }
    case ADLB_DATA_TYPE_MULTISET:
      dc = xlb_multiset_cleanup(d->MULTISET, true, true, ADLB_NO_RC,
                                NO_SCAVENGE);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_STRUCT:
      xlb_free_struct(d->STRUCT, true);
      break;
    // Types with no malloced storage:
    case ADLB_DATA_TYPE_INTEGER:
    case ADLB_DATA_TYPE_FLOAT:
    case ADLB_DATA_TYPE_REF:
    case ADLB_DATA_TYPE_FILE_REF:
      break;
    default:
      check_verbose(false, ADLB_DATA_ERROR_TYPE,
                    "datum_gc(): unknown type %u", type);
      break;
  }
  return ADLB_DATA_SUCCESS;
}

char *ADLB_Data_repr(const adlb_datum_storage *d, adlb_data_type type)
{
  int rc;
  adlb_data_code dc;
  char *tmp;
  switch (type)
  {
    case ADLB_DATA_TYPE_STRING:
    {
      // Allocate with enough room for trailing ...
      tmp = malloc((size_t)d->STRING.length + 5);
      strcpy(tmp, d->STRING.value);
      int pos = 0;
      // Don't return multiple lines of multi-line string
      while (tmp[pos] != '\0')
      {
        if (tmp[pos] == '\n')
        {
          tmp[pos++] = '.';
          tmp[pos++] = '.';
          tmp[pos++] = '.';
          tmp[pos++] = '\0';
          break;
        }
        pos++;
      }
      return tmp;
    }
    case ADLB_DATA_TYPE_INTEGER:
      rc = asprintf(&tmp, "%"PRId64"", d->INTEGER);
      assert(rc >= 0);
      return tmp;
    case ADLB_DATA_TYPE_REF:
      rc = asprintf(&tmp, "<%"PRId64">", d->REF);
      assert(rc >= 0);
      return tmp;
    case ADLB_DATA_TYPE_FLOAT:
      rc = asprintf(&tmp, "%lf", d->FLOAT);
      assert(rc >= 0);
      return tmp;
      break;
    case ADLB_DATA_TYPE_BLOB:
      rc = asprintf(&tmp, "blob (%d bytes)", d->BLOB.length);
      assert(rc >= 0);
      return tmp;
    case ADLB_DATA_TYPE_FILE_REF:
      rc = asprintf(&tmp, "status:<%"PRId64"> filename:<%"PRId64"> mapped:%i",
                    d->FILE_REF.status_id, d->FILE_REF.filename_id,
                    d->FILE_REF.mapped);
      assert(rc >= 0);
      return tmp;
    case ADLB_DATA_TYPE_CONTAINER:
      return data_repr_container(&d->CONTAINER);
    case ADLB_DATA_TYPE_MULTISET:
      dc = xlb_multiset_repr(d->MULTISET, &tmp);
      assert(dc == ADLB_DATA_SUCCESS);
      return tmp;
    case ADLB_DATA_TYPE_STRUCT:
      return xlb_struct_repr(d->STRUCT);
    default:
      rc = asprintf(&tmp, "unknown type: %i\n", type);
      assert(rc >= 0);
      return tmp;
      break;
  }
  return strdup("???");
}

static char *data_repr_container(const adlb_container *c)
{
  adlb_data_code dc;
  struct table_bp* members = c->members;
  size_t cont_str_len = 1024;
  char *cont_str = malloc(cont_str_len);
  int cont_str_pos = 0;

  const char *kts = ADLB_Data_type_tostring(c->key_type);
  const char *vts = ADLB_Data_type_tostring(c->val_type);
  dc = xlb_resize_str(&cont_str, &cont_str_len, cont_str_pos,
                 strlen(kts) + strlen(vts) + 4);
  assert(dc == ADLB_DATA_SUCCESS);
  cont_str_pos += sprintf(&cont_str[cont_str_pos], "%s=>%s: ", kts, vts);

  for (int i = 0; i < members->capacity; i++)
  {
    struct list_bp* L = members->array[i];
    for (struct list_bp_item* item = L->head; item;
         item = item->next)
    {
      adlb_container_val v = item->data;
      char *value_s = ADLB_Data_repr(v, c->val_type);
      dc = xlb_resize_str(&cont_str, &cont_str_len, cont_str_pos,
                     (item->key_len - 1) + strlen(value_s) + 7);
      assert(dc == ADLB_DATA_SUCCESS);
      if (c->key_type == ADLB_DATA_TYPE_STRING)
      {
        cont_str_pos += sprintf(&cont_str[cont_str_pos], "\"%s\"={%s} ",
                                (char*)item->key, value_s);
      }
      else
      {
        // TODO: support binary keys
        cont_str_pos += sprintf(&cont_str[cont_str_pos], "\"%s\"={%s} ",
                                (char*)item->key, value_s);
      }

      free(value_s);
    }
  }
  cont_str[cont_str_pos] = '\0';
  return cont_str;
}
