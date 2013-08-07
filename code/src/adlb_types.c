#include "adlb_types.h"

#include <assert.h>
#include <string.h>

#include "adlb.h"
#include "data_cleanup.h"
#include "data_structs.h"

static char *data_repr_container(adlb_container *c);

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
      dc = cleanup_members(&d->CONTAINER, true, ADLB_NO_RC, NO_SCAVENGE);
      DATA_CHECK(dc);
      break;
    }
    case ADLB_DATA_TYPE_MULTISET:
      //TODO: implement for multiset
      //multiset_free(d->MULTISET);
      check_verbose(false, ADLB_DATA_ERROR_UNKNOWN,
          "free not implemented for multiset");
      break;
    case ADLB_DATA_TYPE_STRUCT:
      data_free_struct(d->STRUCT, true);
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



// Check string buffer is big enough for needed chars + a terminating null byte
static adlb_data_code check_str_size(char **str, size_t *curr_size, int pos,
                                     size_t needed)
{
  assert(pos >= 0);
  size_t total_needed = ((size_t)pos) + needed + 1;
  if (total_needed > *curr_size)
  {
    size_t new_size = *curr_size + 1024;
    if (new_size < total_needed)
      new_size = total_needed + 1024;

    char *new = realloc(*str, new_size);
    if (new == NULL)
      return ADLB_DATA_ERROR_OOM;
    *str = new;
    *curr_size = new_size;
  }
  return ADLB_DATA_SUCCESS;
}

char *ADLB_Data_repr(adlb_datum_storage *d, adlb_data_type type)
{
  int rc;
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
      rc = asprintf(&tmp, "%lli", d->INTEGER);
      assert(rc >= 0);
      return tmp;
    case ADLB_DATA_TYPE_REF:
      rc = asprintf(&tmp, "<%lli>", d->REF);
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
      rc = asprintf(&tmp, "status:<%lli> filename:<%lli> mapped:%i",
            d->FILE_REF.status_id, d->FILE_REF.filename_id, d->FILE_REF.mapped);
      assert(rc >= 0);
      return tmp;
    case ADLB_DATA_TYPE_CONTAINER:
      return data_repr_container(&d->CONTAINER);
    case ADLB_DATA_TYPE_MULTISET:
      // TODO:
      return strdup("MULTISET???");
    case ADLB_DATA_TYPE_STRUCT:
      return data_struct_repr(d->STRUCT);
    default:
      rc = asprintf(&tmp, "unknown type: %i\n", type);
      assert(rc >= 0);
      return tmp;
      break;
  }
  return strdup("???");
}

static char *data_repr_container(adlb_container *c)
{
  adlb_data_code dc;
  struct table* members = c->members;
  size_t cont_str_len = 1024;
  char *cont_str = malloc(cont_str_len);
  int cont_str_pos = 0;

  const char *kts = ADLB_Data_type_tostring(c->key_type);
  const char *vts = ADLB_Data_type_tostring(c->val_type);
  dc = check_str_size(&cont_str, &cont_str_len, cont_str_pos,
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
      dc = check_str_size(&cont_str, &cont_str_len, cont_str_pos,
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
