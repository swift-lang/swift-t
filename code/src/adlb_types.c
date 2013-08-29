
#define _GNU_SOURCE // for asprintf()

#include "adlb_types.h"

#include <stdio.h>

#include <vint.h>

#include "adlb.h"
#include "data_cleanup.h"
#include "data_structs.h"
#include "multiset.h"

static char *data_repr_container(const adlb_container *c);

adlb_data_code
ADLB_Pack(const adlb_datum_storage *d, adlb_data_type type,
          const adlb_buffer *caller_buffer,
          adlb_binary_data *result)
{
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
      verbose_error(ADLB_DATA_ERROR_TYPE,
        "Serialization of containers not yet supported!\n");
    case ADLB_DATA_TYPE_MULTISET:
      verbose_error(ADLB_DATA_ERROR_TYPE,
        "Serialization of multisets not yet supported!\n");
    default:
      verbose_error(ADLB_DATA_ERROR_TYPE,
        "Cannot serialize unknown type %i!\n", type);
  }
}

adlb_data_code
ADLB_Pack_buffer(const adlb_datum_storage *d, adlb_data_type type,
        const adlb_buffer *tmp_buf, adlb_buffer *output,
        bool *output_caller_buffer, int *output_pos)
{
  adlb_data_code dc;

  // Get binary representation of datum
  adlb_binary_data packed;
  dc = ADLB_Pack(d, type, tmp_buf, &packed); 
  DATA_CHECK(dc);

  // Check buffer large enough for this member
  int required_size = *output_pos + (int)VINT_MAX_BYTES + packed.length;
  ADLB_Resize_buf(output, output_caller_buffer, required_size);

  // Prefix with length of member
  int vint_len = vint_encode(packed.length, output->data + *output_pos);
  assert(vint_len >= 1);
  *output_pos += vint_len;

  // Copy in data
  assert(packed.length >= 0);
  memcpy(output->data + *output_pos, packed.data, (size_t)packed.length);
  *output_pos += packed.length;
  
  // Free any malloced temporary memory
  if (packed.data != tmp_buf->data)
    ADLB_Free_binary_data(&packed);

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
  struct table* members = c->members;
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
    struct list_sp* L = members->array[i];
    for (struct list_sp_item* item = L->head; item;
         item = item->next)
    {
      char *key = item->key;
      adlb_container_val v = item->data;
      char *value_s = ADLB_Data_repr(v, c->val_type);
      dc = xlb_resize_str(&cont_str, &cont_str_len, cont_str_pos,
                     strlen(key) + strlen(value_s) + 7);
      assert(dc == ADLB_DATA_SUCCESS);
      cont_str_pos += sprintf(&cont_str[cont_str_pos], "\"%s\"={%s} ",
                              key, value_s);

      free(value_s);
    }
  }
  cont_str[cont_str_pos] = '\0';
  return cont_str;
}
