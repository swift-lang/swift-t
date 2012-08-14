
// Required for GNU asprintf()
#define _GNU_SOURCE

#include <assert.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "adlb.h"
#include "data.h"
#include <table_lp.h>
#include <table.h>
#include <list_i.h>
#include <list_l.h>

/**
   Map from adlb_datum_id to adlb_datum
*/
struct table_lp tds;

/**
   Map from "container,subscript" specifier to list of listeners
 */
struct table container_listeners;

/**
   Map from container id to number of slots allocated (integer)
 */
struct table_lp container_slots;

/**
   Map from adlb_datum_id to int rank if locked
*/
struct table_lp locked;

/**
   Number of ADLB servers
*/
static int servers = 1;

/**
   Unique datum id.  Note that 0 is ADLB_DATA_ID_NULL.
*/
static adlb_datum_id unique = -1;

/**
   When data_unique hits this, return an error- we have exhausted the
   longs. Defined in data_init()
 */
static adlb_datum_id last_id;

#ifndef NDEBUG
/**
    Allows user to check an exceptional condition,
    print an error message, and return an error code in one swoop.
    This is disabled if NDEBUG is set
*/
#define check_verbose(condition, code, format, args...) \
  check_verbose_impl(condition, code,                   \
                     __FILE__, __LINE__, __FUNCTION__,  \
                     format, ## args)

#define check_verbose_impl(condition, code, file, line, func, \
                           format, args...)                   \
  { if (! (condition))                                        \
    {                                                         \
      printf("ADLB DATA ERROR:\n");                           \
      printf(format "\n", ## args);                           \
      printf("\t in: %s\n", func);                            \
      printf("\t at: %s:%i\n", file, line);                   \
      return code;                                            \
    }                                                         \
  }

// TODO:

#else
// Make this a noop if NDEBUG is set (for performance)
#define check_verbose(condition, code, format, args...)
#endif

/**
   @param s Number of servers
   @param r My rank
 */
adlb_data_code
data_init(int s, int r)
{
  servers = s;
  unique = r;

  bool result;
  result = table_lp_init(&tds, 1024*1024);
  if (!result)
    return ADLB_DATA_ERROR_OOM;
  result = table_init(&container_listeners, 1024*1024);
  if (!result)
    return ADLB_DATA_ERROR_OOM;

  result = table_lp_init(&container_slots, 1024);
  if (!result)
    return ADLB_DATA_ERROR_OOM;

  result = table_lp_init(&locked, 16);
  if (!result)
    return ADLB_DATA_ERROR_OOM;

  last_id = LONG_MAX - servers - 1;

  return ADLB_DATA_SUCCESS;
}

adlb_data_code
data_create(adlb_datum_id id, adlb_data_type type)
{
  adlb_data_code result;
  check_verbose(id > 0, ADLB_DATA_ERROR_INVALID,
                "ERROR: attempt to create data: id=%li\n", id);

  adlb_datum* d = malloc(sizeof(adlb_datum));
  d->type = type;
  d->status = ADLB_DATA_UNSET;
  list_i_init(&d->listeners);

  table_lp_add(&tds, id, d);
  return ADLB_DATA_SUCCESS;
}

/**
   file-type data should have the file name set at creation time
   This function makes a copy of the given file name
 */
adlb_data_code
data_create_filename(adlb_datum_id id, const char* filename)
{
  DEBUG("data_create_filename(%li, %s)", id, filename);
  adlb_datum* d = table_lp_search(&tds, id);

  // This can only fail on an internal error
  assert(d != NULL);

  d->data.FILE.path = strdup(filename);
  return ADLB_DATA_SUCCESS;
}

/**
   container-type data should have the subscript type set at creation
   time
 */
adlb_data_code
data_create_container(adlb_datum_id id, adlb_data_type type)
{
  adlb_datum* d = table_lp_search(&tds, id);
  // This can only fail on an internal error
  assert(d != NULL);
  d->data.CONTAINER.members = table_create(1024);
  d->data.CONTAINER.type = type;
  int* z = malloc(sizeof(int));
  *z = 1;
  table_lp_add(&container_slots, id, z);
  return ADLB_DATA_SUCCESS;
}

void
data_exists(adlb_datum_id id, bool* result)
{
  adlb_datum* d = table_lp_search(&tds, id);

  if (d == NULL ||
      d->status == ADLB_DATA_UNSET)
    *result = false;
  else
    *result = true;
}

adlb_data_code
data_typeof(adlb_datum_id id, adlb_data_type* type)
{
  check_verbose(id != ADLB_DATA_ID_NULL, ADLB_DATA_ERROR_NULL,
                "given ADLB_DATA_ID_NULL");

  adlb_datum* d = table_lp_search(&tds, id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);

  *type = d->type;
  DEBUG("typeof: <%li> => %i", id, *type);
  return ADLB_DATA_SUCCESS;
}

/**
   @param type output: the type of the subscript
               for the given container id
 */
adlb_data_code
data_container_typeof(adlb_datum_id id, adlb_data_type* type)
{
  adlb_datum* d = table_lp_search(&tds, id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);

  adlb_data_type t = d->type;
  check_verbose(t == ADLB_DATA_TYPE_CONTAINER, ADLB_DATA_ERROR_TYPE,
                "not a container: <%li>", id);
  *type = d->data.CONTAINER.type;
  DEBUG("container_type: <%li> => %i", id, *type);
  return ADLB_DATA_SUCCESS;
}

/**
   Allocates fresh memory in result unless count==0
   Caller must free result
 */
adlb_data_code
data_close(adlb_datum_id id, int** result, int* count)
{
  check_verbose(id != ADLB_DATA_ID_NULL,
                ADLB_DATA_ERROR_NULL, "NULL: <%li>", id);

  adlb_datum* d = table_lp_search(&tds, id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);
  check_verbose(d->status == ADLB_DATA_UNSET,
                ADLB_DATA_ERROR_DOUBLE_WRITE,
                "double write: <%li>", id);
  d->status = ADLB_DATA_SET;

  list_i_toints(&d->listeners, result, count);
  TRACE("data_close: <%li> count: %i\n", id, *count);
  return ADLB_DATA_SUCCESS;
}

static void garbage_collect(adlb_datum_id id, adlb_datum* d,
                            char** output, int* output_length);

/**
   @param output: If the d was a container and it was decremented to
                  0 and garbage-collected, this is the
                  record-separated list of its members.  The caller
                  will probably want to decrement each of them.
                  Else, NULL.
 */
adlb_data_code
data_reference_count(adlb_datum_id id, int increment,
                     char** output, int* output_length)
{
  adlb_datum* d = table_lp_search(&tds, id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);
  d->reference_count += increment;
  // This is usually NULL
  *output = NULL;
  if (d->reference_count <= 0)
    garbage_collect(id, d, output, output_length);
  return ADLB_SUCCESS;
}

static void
extract_members(struct table* members, int count, int offset,
                char** output, int* output_length);

static void
garbage_collect(adlb_datum_id id, adlb_datum* d,
                char** output, int* output_length)
{
  switch (d->type)
  {
    ADLB_DATA_TYPE_STRING:
    {
      free(d->data.STRING.value);
      break;
    }
    ADLB_DATA_TYPE_BLOB:
    {
      free(d->data.BLOB.value);
      break;
    }
    ADLB_DATA_TYPE_CONTAINER:
    {
      struct table* members = d->data.CONTAINER.members;
      extract_members(members, INT_MAX, 0, output, output_length);
      table_lp_remove(&container_slots, id);
      break;
    }
    // These two are easy:
    ADLB_DATA_TYPE_INTEGER:
    ADLB_DATA_TYPE_FLOAT:
      break;
    default:
      assert(false);
      break;
  }
  // I think logically this list should be empty:
  assert(d->listeners.size == 0);
  table_lp_remove(&tds, id);
  free(d);
}

adlb_data_code
data_lock(adlb_datum_id id, int rank, bool* result)
{
  adlb_datum* d = table_lp_search(&tds, id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);

  if (table_lp_search(&locked, id))
  {
    *result = false;
    return ADLB_DATA_SUCCESS;
  }
  else
  {
    int* r = malloc(sizeof(int));
    *r = rank;
    *result = true;
    table_lp_add(&locked, id, r);
  }

  return ADLB_DATA_SUCCESS;
}

adlb_data_code
data_unlock(adlb_datum_id id)
{
  int* r = table_lp_remove(&locked, id);
  check_verbose(r != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);
  free(r);
  return ADLB_DATA_SUCCESS;
}

/**
   @param result set to 1 iff subscribed, else 0 (td closed)
   @return ADLB_SUCCESS or ADLB_ERROR
 */
adlb_data_code
data_subscribe(adlb_datum_id id, int rank, int* result)
{
  adlb_datum* d = table_lp_search(&tds, id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);

  if (d->status == ADLB_DATA_SET)
  {
    *result = 0;
  }
  else
  {
    list_i_unique_insert(&d->listeners, rank);
    *result = 1;
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
data_container_reference(adlb_datum_id container_id,
                         const char* subscript,
                         adlb_datum_id reference,
                         adlb_datum_id* member)
{
  // Check that container_id is an initialized container
  adlb_datum* d = table_lp_search(&tds, container_id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", container_id);

  // Is the subscript already pointing to a data identifier?
  void* t = table_search(d->data.CONTAINER.members, subscript);
  if (t != NULL)
  {
    char* z;
    long m = strtol(t, &z, 10);
    if (z == t)
      return ADLB_DATA_ERROR_NUMBER_FORMAT;
    if (m != ADLB_DATA_ID_UNLINKED)
    {
      *member = m;
      return ADLB_DATA_SUCCESS;
    }
  }

  // Is the container closed?
  check_verbose(d->status != ADLB_DATA_SET, ADLB_DATA_ERROR_INVALID,
                "Attempting to subscribe to non-existent subscript\n"
                "on a closed container:  %li[%s]\n",
                container_id, subscript);

  char* pair;
  int length = asprintf(&pair, "%li[%s]", container_id, subscript);
  check_verbose(length > 0, ADLB_DATA_ERROR_OOM,
                "OUT OF MEMORY");

  struct list_l* listeners =
      table_search(&container_listeners, pair);
  if (!listeners)
  {
    // Nobody else has subscribed to this pair yet
    listeners = list_l_create();
    table_add(&container_listeners, pair, listeners);
  }

  list_l_unique_insert(listeners, reference);
  *member = 0;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
data_store(adlb_datum_id id, void* buffer, int length)
{
  TRACE("data_store(%li)\n", id);

  adlb_datum* d = table_lp_search(&tds, id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);

  adlb_data_type type = d->type;

  int n;
  switch (type)
  {
    case ADLB_DATA_TYPE_STRING:
      d->data.STRING.value = strdup(buffer);
      d->data.STRING.length = length;
      break;
    case ADLB_DATA_TYPE_INTEGER:
      d->data.INTEGER.value = *(long*) buffer;
      break;
    case ADLB_DATA_TYPE_FLOAT:
      memcpy(&d->data.FLOAT.value, buffer, sizeof(double));
      break;
    case ADLB_DATA_TYPE_BLOB:
      d->data.BLOB.value = malloc(length);
      memcpy(d->data.BLOB.value, buffer, length);
      d->data.BLOB.length = length;
      break;
    case ADLB_DATA_TYPE_FILE:
      // closed- do nothing
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      // closed- do nothing
      break;
    default:
      printf("data_store(): unknown type: %i\n", type);
      return ADLB_DATA_ERROR_INVALID;
      break;
  }

  return ADLB_DATA_SUCCESS;
}

/**
   Used by data_retrieve()
*/
#define CHECK_SET(id, d)                              \
  if (d->status == ADLB_DATA_UNSET) {                 \
    printf("not set: %li\n", id);    \
    return ADLB_DATA_ERROR_UNSET;                     \
  }

/**
   Retrieve works on UNSET data for files and containers:
                  this is necessary for filenames,
                  and may be useful for containers.
   Callee should use result before making further calls into
   this module, except if type is container, in which case the
   callee must free result pointer.  This is because the container
   subscript list must be dynamically created.
   @param type   Returns type
   @param result Returns pointer to data in data module memory,
                 except if type is container, in which case returns
                 fresh memory
   @param length Returns length of string
 */
adlb_data_code
data_retrieve(adlb_datum_id id, adlb_data_type* type,
	      void** result, int* length)
{
  TRACE("data_retrieve(%li)", id);
  adlb_datum* d = table_lp_search(&tds, id);

  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", id);

  *type = d->type;
  switch (d->type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      CHECK_SET(id, d);
      *result = &d->data.INTEGER.value;
      *length = sizeof(long);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      CHECK_SET(id, d);
      *result = &d->data.FLOAT.value;
      *length = sizeof(double);
      break;
    case ADLB_DATA_TYPE_STRING:
      CHECK_SET(id, d);
      *result = d->data.STRING.value;
      *length = d->data.STRING.length;
      break;
    case ADLB_DATA_TYPE_BLOB:
      CHECK_SET(id, d);
      *result = d->data.BLOB.value;
      *length = d->data.BLOB.length;
      break;
    case ADLB_DATA_TYPE_FILE:
      *result = d->data.FILE.path;
      *length = strlen(d->data.FILE.path)+1;
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      printf("data_retrieve(): may not be used on containers!\n");
      return ADLB_DATA_ERROR_TYPE;
    default:
      printf("data_retrieve(): unknown type!\n");
      return ADLB_DATA_ERROR_TYPE;
  }
  return ADLB_DATA_SUCCESS;
}

/**
   Extract the table members into a big string
 */
static void
extract_members(struct table* members, int count, int offset,
                char** output, int* output_length)
{
  // Pointer into output string
  char* p;
  int c = 0;
  int size = members->size;
  char* A = malloc(size*ADLB_DATA_MEMBER_MAX*sizeof(char));
  p = A;
  for (int i = 0; i < members->capacity; i++)
  {
    struct list_sp* L = members->array[i];
    for (struct list_sp_item* item = L->head; item;
         item = item->next)
    {
      if (c < offset)
      {
        c++;
        continue;
      }
      if (c >= count+offset && count != -1)
        break;
      // Copy the member into the output array
      p = stpcpy(p, item->data);
      *p = RS;
      p++;
      c++;
    }
  }
  *output = A;
  *output_length = p - A;
  TRACE("extract_members: output_length: %i\n",
        *output_length);
}

/**
   @param container_id
   @param subscripts If points to non-NULL,
                     output location for space-separated
                                          string of subscripts
   @param length If *subscripts non-NULL, returns the length of the
                 output string
   @param members If points to non-NULL,
                  the output location for array of
                  container member IDs
   @param actual Returns the number of entries in the container
 */
adlb_data_code
data_enumerate(adlb_datum_id container_id, int count, int offset,
               char** subscripts, int* subscripts_length,
               char** members, int* members_length, int* actual)
{
  TRACE("data_enumerate(%li)", container_id);
  adlb_datum* c = table_lp_search(&tds, container_id);

  check_verbose(c != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", container_id);
  check_verbose(c->type == ADLB_DATA_TYPE_CONTAINER,
                ADLB_DATA_ERROR_TYPE,
                "not a container: <%li>", container_id);


  int slice_size = c->data.CONTAINER.members->size - offset;
  if (count != -1) {
    if (slice_size > count) {
      // size of slice limited
      slice_size = count;
    }
  }
  // might be negative
  slice_size = slice_size < 0 ? 0 : slice_size;

  *actual = slice_size;

  if (*subscripts)
    *subscripts_length = table_keys_string_slice(subscripts,
                                      c->data.CONTAINER.members,
                                      count, offset);
  if (*members)
    extract_members(c->data.CONTAINER.members,
                    count, offset, members, members_length);

   return ADLB_DATA_SUCCESS;
}

adlb_data_code
data_container_size(adlb_datum_id container_id, int* size)
{
  adlb_datum* c = table_lp_search(&tds, container_id);

  check_verbose(c != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "not found: <%li>", container_id);
  check_verbose(c->type == ADLB_DATA_TYPE_CONTAINER,
                ADLB_DATA_ERROR_TYPE,
                "not a container: <%li>", container_id);

  *size = c->data.CONTAINER.members->size;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
data_slot_create(adlb_datum_id container_id)
{
  // Check that this is a valid container
  int* slots = table_lp_search(&container_slots, container_id);
  check_verbose(slots != NULL, ADLB_DATA_ERROR_TYPE,
                "not a container: <%li>", container_id);

  *slots = (*slots) + 1;
  DEBUG("slots: <%li> => %i", container_id, *slots);

  return ADLB_DATA_SUCCESS;
}

adlb_data_code
data_slot_drop(adlb_datum_id container_id, int* result)
{
  // Check that this is a valid container
  int* slots = table_lp_search(&container_slots, container_id);
  check_verbose(slots != NULL, ADLB_DATA_ERROR_TYPE,
                "not a container: <%li>", container_id);

  *slots = (*slots) - 1;
  DEBUG("slots: <%li> => %i", container_id, *slots);

  *result = *slots;
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
data_insert(adlb_datum_id container_id,
            const char* subscript, const char* member, int drops,
            adlb_datum_id** references, int* count, int* slots)
{
  adlb_datum* d = table_lp_search(&tds, container_id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "container not found: <%li>", container_id);
  check_verbose(d->type == ADLB_DATA_TYPE_CONTAINER,
                ADLB_DATA_ERROR_TYPE,
                "not a container: <%li>",
                container_id);

  // Does the link already exist?
  void* t = table_search(d->data.CONTAINER.members, subscript);
  if (t != NULL)
  {
    // Assert that this is an UNLINKED entry:
    check_verbose(atol(t) == ADLB_DATA_ID_UNLINKED,
                  ADLB_DATA_ERROR_DOUBLE_WRITE,
                  "already exists: <%li>[%s]",
                  container_id, subscript);

    // Ok- somebody did an Insert_atomic
    char* s;
    void* v;
    // Reset entry
    // Value v is UNLINKED, just free it
    bool b =
        table_set(d->data.CONTAINER.members, subscript, member, &v);
    assert(b);
    free(v);
  }
  else
  {
    // Copy key/value onto the heap so we can store them
    subscript = strdup(subscript);
    table_add(d->data.CONTAINER.members, subscript, member);
  }

  // Drop slot count for this container
  int* p  = table_lp_search(&container_slots, container_id);
  *p = (*p) - drops;
  *slots = *p;
  DEBUG("slots: <%li> => %i", container_id, *slots);
  if (slots < 0)
    return ADLB_DATA_ERROR_SLOTS_NEGATIVE;

  // Find, remove, and return any listening container references
  char s[ADLB_DATA_SUBSCRIPT_MAX+32];
  sprintf(s, "%li[%s]", container_id, subscript);
  void* data;
  bool result = table_remove(&container_listeners, s, &data);
  struct list_l* listeners = data;
  if (result)
  {
    list_l_tolongs(listeners, references, count);
    list_l_free(listeners);
  }
  else
    *count = 0;

  TRACE("data_insert(): DONE");
  return ADLB_DATA_SUCCESS;
}


adlb_data_code
data_insert_atomic(adlb_datum_id container_id, const char* subscript,
                   bool* result)
{
  adlb_datum* d = table_lp_search(&tds, container_id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "container not found: <%li>", container_id);
  check_verbose(d->type == ADLB_DATA_TYPE_CONTAINER,
                ADLB_DATA_ERROR_TYPE,
                "not a container: <%li>", container_id);

  // Does the link already exist?
  if (table_contains(d->data.CONTAINER.members, subscript))
  {
    *result = false;
    return ADLB_DATA_SUCCESS;
  }

  // Copy key/value onto the heap so we can store them
  subscript = strdup(subscript);
  // Regression check:
  assert(ADLB_DATA_ID_UNLINKED == -1);
  char* member = strdup("-1");
  table_add(d->data.CONTAINER.members, subscript, member);
  *result = true;
  return ADLB_DATA_SUCCESS;
}

/**
   Look in container id for given subscript
   @param result output: found container member or
                 undefined if subscript not found.
 */
adlb_data_code
data_lookup(adlb_datum_id id, const char* subscript,
            char** result)
{
  adlb_datum* d = table_lp_search(&tds, id);
  check_verbose(d != NULL, ADLB_DATA_ERROR_NOT_FOUND,
                "container not found: <%li>\n", id);
  check_verbose(d->type == ADLB_DATA_TYPE_CONTAINER,
                ADLB_DATA_ERROR_TYPE,
                "not a container: <%li>", id);

  void* t = table_search(d->data.CONTAINER.members, subscript);
  if (t == NULL)
  {
    *result = ADLB_DATA_ID_NULL;
    return ADLB_DATA_SUCCESS;
  }
  if (t == (char*) ADLB_DATA_ID_UNLINKED)
  {
    *result = ADLB_DATA_ID_NULL;
    return ADLB_DATA_SUCCESS;
  }

  *result = t;
  return ADLB_DATA_SUCCESS;
}

/**
   Obtain an unused TD
   @return Successful unless we have exhausted the
           set of signed long integers,
           in which case return ADLB_DATA_ID_NULL
 */
adlb_data_code
data_unique(adlb_datum_id* result)
{
  while (true)
  {
    if (unique >= last_id)
    {
      *result = ADLB_DATA_ID_NULL;
      return ADLB_DATA_ERROR_LIMIT;
    }
    adlb_datum* td = table_lp_search(&tds, unique);
    if (td == NULL)
      break;
    unique += servers;
  }
  *result = unique;
  unique += servers;
  return ADLB_DATA_SUCCESS;
}

/**
   Convert string representation of data type to data type number
 */
void
ADLB_Data_string_totype(const char* type_string,
                        adlb_data_type* type)
{
  if (strcmp(type_string, "integer") == 0)
    *type = ADLB_DATA_TYPE_INTEGER;
  else if (strcmp(type_string, "float") == 0)
    *type = ADLB_DATA_TYPE_FLOAT;
  else if (strcmp(type_string, "string") == 0)
    *type = ADLB_DATA_TYPE_STRING;
  else if (strcmp(type_string, "blob") == 0)
    *type = ADLB_DATA_TYPE_BLOB;
  else if (strcmp(type_string, "file") == 0)
    *type = ADLB_DATA_TYPE_FILE;
  else if (strcmp(type_string, "container") == 0)
    *type = ADLB_DATA_TYPE_CONTAINER;
  else
    *type = ADLB_DATA_TYPE_NULL;
}

/**
   Convert given data type number to output string representation
 */
int
ADLB_Data_type_tostring(char* output, adlb_data_type type)
{
  int result = -1;
  switch(type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      result = sprintf(output, "integer");
      break;
    case ADLB_DATA_TYPE_FLOAT:
      result = sprintf(output, "float");
      break;
    case ADLB_DATA_TYPE_STRING:
      result = sprintf(output, "string");
      break;
    case ADLB_DATA_TYPE_BLOB:
      result = sprintf(output, "blob");
      break;
    case ADLB_DATA_TYPE_FILE:
      result = sprintf(output, "file");
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      result = sprintf(output, "container");
      break;
    case ADLB_DATA_TYPE_NULL:
      sprintf(output, "ADLB_DATA_TYPE_NULL");
      break;
    default:
      sprintf(output, "<unknown type>");
      break;
  }
  return result;
}

void report_leaks(void);

adlb_data_code
data_finalize()
{
  report_leaks();
  return ADLB_DATA_SUCCESS;
}

void report_leaks()
{
  for (int i = 0; i < tds.capacity; i++)
  {
    struct list_lp* L = tds.array[i];
    for (struct list_lp_item* item = L->head; item; item = item->next)
    {
      DEBUG("LEAK: %li\n", item->key);
    }
  }
}
