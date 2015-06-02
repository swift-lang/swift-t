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

#include "table_bp.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "binkeys.h"
#include "list_bp.h"
#include "c-utils-types.h"

// Double in size for now
static const float table_bp_expand_factor = 2.0;

static void
table_bp_dump2(const char* format, const table_bp* target,
               bool include_vals);

static table_bp_entry*
find_bucket(const table_bp* T, const void* key, size_t key_len);

static bool
bucket_add_head(table_bp_entry *head, binkey_packed_t key, void* data);

static bool
bucket_add_tail(table_bp_entry *head, table_bp_entry *entry,
                bool copy_entry);

static table_bp_entry*
table_bp_locate_entry(const table_bp *T, const void *key, size_t key_len,
                      table_bp_entry **prev);

static void
table_bp_remove_entry(table_bp_entry *e, table_bp_entry *prev);

static bool
table_bp_expand(table_bp *T);

static size_t
bucket_keys_string_length(const table_bp_entry *head);

static size_t
bucket_keys_tostring(char *result, const table_bp_entry *head);

static size_t
bucket_tostring(char *result, size_t size, const char *format,
                const table_bp_entry *head);

static int
calc_resize_threshold(table_bp *T)
{
  return (int)((float)T->capacity * T->load_factor);
}

bool table_bp_init(table_bp* target, int capacity)
{
  return table_bp_init_custom(target, capacity,
                              TABLE_BP_DEFAULT_LOAD_FACTOR);
}

/**

*/
bool
table_bp_init_custom(table_bp* target, int capacity, float load_factor)
{
  assert(capacity >= 1);
  target->size = 0;
  target->capacity = capacity;
  target->load_factor = load_factor;
  target->resize_threshold = calc_resize_threshold(target);

  target->array = malloc(sizeof(target->array[0]) * (size_t)capacity);
  if (!target->array)
  {
    return false;
  }

  for (int i = 0; i < capacity; i++)
  {
    table_bp_clear_entry(&target->array[i]);
  }
  return true;
}

table_bp*
table_bp_create(int capacity)
{
  return table_bp_create_custom(capacity, TABLE_BP_DEFAULT_LOAD_FACTOR);
}

table_bp*
table_bp_create_custom(int capacity, float load_factor)
{
  table_bp* new_table =  malloc(sizeof(const table_bp));
  if (! new_table)
    return NULL;

  bool result = table_bp_init_custom(new_table, capacity, load_factor);
  if (!result)
  {
    free(new_table);
    return NULL;
  }

  return new_table;
}

void
table_bp_free(table_bp* target)
{
  table_bp_free_callback(target, true, NULL);
}

void table_bp_free_callback(table_bp* target, bool free_root,
                         void (*callback)(const void*, size_t, void*))
{
  for (int i = 0; i < target->capacity; i++)
  {
    table_bp_entry *head = &target->array[i];
    if (table_bp_entry_valid(head))
    {
      // Store next pointer to allow freeing entry
      bool is_head;
      table_bp_entry *e, *next;
      for (e = head, next = head->next, is_head = true;
           e != NULL;
           e = next, is_head = false)
      {
        next = e->next; // Store next right away

        if (callback != NULL)
          callback(table_bp_get_key(e), table_bp_key_len(e), e->data);

        table_bp_free_entry(e, is_head);
      }
    }
  }

  free(target->array);

  if (free_root)
  {
    free(target);
  }
  else
  {
    target->array = NULL;
    target->capacity = target->size = 0;
  }
}

void
table_bp_destroy(table_bp* target)
{
  for (int i = 0; i < target->capacity; i++)
  {
    table_bp_entry *head = &target->array[i];
    if (table_bp_entry_valid(head))
    {
      // Store next pointer to allow freeing entry
      bool is_head;
      table_bp_entry *e, *next;
      for (e = head, next = head->next, is_head = true;
           e != NULL;
           e = next, is_head = false)
      {
        next = e->next; // Store next right away

        binkey_packed_free(&e->key);

        free(e->data);

        if (!is_head)
        {
         free(e);
        }
      }
    }
  }

  free(target->array);
  free(target);
}

void
table_bp_release(table_bp* target)
{
  free(target->array);
}

/**
  Return the head of the appropriate bucket for the key
 */
static table_bp_entry*
find_bucket(const table_bp* T, const void* key, size_t key_len)
{
  int index = binkey_hash(key, key_len, T->capacity);
  return &T->array[index];
}

/*
 Add at head
 key: appropriate representation with inlining
 */
static bool
bucket_add_head(table_bp_entry *head, binkey_packed_t key, void* data)
{
  if (table_bp_entry_valid(head))
  {
    // Head occupied: replace head with this one
    table_bp_entry *e = malloc(sizeof(table_bp_entry));
    if (e == NULL)
    {
      return false;
    }
    // Copy current head to e
    memcpy(e, head, sizeof(*e));
    head->next = e;
  }

  // Put new data in head
  head->key = key;
  head->data = data;
  return true;
}

/**
  Add at end of bucket chain
  copy_entry: if true, allocate new entry, otherwise reuse provided
    entry or free it.  In no case do we copy the key or data memory.
 */
static bool
bucket_add_tail(table_bp_entry *head, table_bp_entry *entry,
                bool copy_entry)
{
  if (table_bp_entry_valid(head))
  {
    // Head occupied: find tail and add
    if (copy_entry)
    {
      table_bp_entry *new_entry = malloc(sizeof(*entry));
      if (new_entry == NULL)
      {
        return false;
      }
      memcpy(new_entry, entry, sizeof(*entry));
      entry = new_entry;
    }

    table_bp_entry *tail = head; // Find tail of list
    while (tail->next != NULL)
    {
      tail = tail->next;
    }
    tail->next = entry;
    entry->next = NULL;
  }
  else
  {
    assert(head->next == NULL); // Check no dangling
    // Empty list case
    memcpy(head, entry, sizeof(*head));
    head->next = NULL;

    if (!copy_entry)
    {
      // Don't need memory since we copied to head
      free(entry);
    }
  }

  return true;
}

/**
   Note: duplicates internal copy of key
 */
bool
table_bp_add(table_bp *target, const void* key, size_t key_len,
             void* data)
{
  // Check to resize hash table
  if (target->size > target->resize_threshold)
  {
    bool ok = table_bp_expand(target);
    if (!ok)
      return false;
  }

  /*
   * Add at head of list to avoid traversing list.  This means that in
   * case of duplicate keys, the newest is returned
   */
  table_bp_entry *head = find_bucket(target, key, key_len);

  binkey_packed_t key_repr;

  bool ok = binkey_packed_set(&key_repr, key, key_len);
  if (!ok)
  {
    return false;
  }

  ok = bucket_add_head(head, key_repr, data);
  if (ok)
  {
    target->size++;
    return true;
  }
  else
  {
    // Free allocated key
    binkey_packed_free(&key_repr);
    return false;
  }
}

/*
  Find entry in table matching key
  prev: if provided, filled with previous entry
  returns: NULL if not found
 */
static table_bp_entry *
table_bp_locate_entry(const table_bp *T, const void *key, size_t key_len,
                      table_bp_entry **prev)
{
  table_bp_entry *prev_e = NULL;
  table_bp_entry *head = find_bucket(T, key, key_len);
  if (!table_bp_entry_valid(head)) // Empty bucket
    return NULL;

  for (table_bp_entry *e = head; e != NULL; e = e->next)
  {
    if (table_bp_key_match(key, key_len, e))
    {
      if (prev != NULL)
      {
        *prev = prev_e;
      }
      return e;
    }
    prev_e = e;
  }
  return NULL;
}

/**
   If found, caller is responsible for old_value -
          it was provided by the user
   @return True if found
 */
bool
table_bp_set(table_bp* target, const void* key, size_t key_len,
          void* value, void** old_value)
{
  table_bp_entry *e = table_bp_locate_entry(target, key, key_len, NULL);
  if (e != NULL)
  {
    *old_value = e->data;
    e->data = value;
    return true;
  }

  return false;
}

/**
   @param value: this is used to return the value if found
   @return true if found, false if not

   if value is NULL and return is true, this means that the key exists
   but the value is NULL
*/
bool
table_bp_search(const table_bp* table, const void* key,
                size_t key_len, void **value)
{
  table_bp_entry *e = table_bp_locate_entry(table, key, key_len, NULL);

  if (e != NULL)
  {
    *value = e->data;
    return true;
  }
  else
  {
    *value = NULL;
    return false;
  }
}

bool
table_bp_contains(const table_bp* table, const void* key,
                  size_t key_len)
{
  void* tmp = NULL;
  return table_bp_search(table, key, key_len, &tmp);
}

/*
 Unlink and free entry if needed
 prev: previous entry, or NULL if list head
 */
static void
table_bp_remove_entry(table_bp_entry *e, table_bp_entry *prev)
{
  if (prev == NULL)
  {
    // Removing head of list
    if (e->next == NULL)
    {
      // List is now empty - reset
      table_bp_clear_entry(e);
    }
    else
    {
      // Promote other entry to head
      table_bp_entry *next = e->next;
      memcpy(e, next, sizeof(*e));
      free(next);
    }
  }
  else
  {
    prev->next = e->next;
    free(e); // Was malloced separately
  }
}

bool
table_bp_remove(table_bp* table, const void* key, size_t key_len,
                void** data)
{
  table_bp_entry *prev;
  table_bp_entry *e = table_bp_locate_entry(table, key, key_len, &prev);
  if (e != NULL)
  {
    *data = e->data; // Store data for caller
    binkey_packed_free(&e->key);

    table_bp_remove_entry(e, prev);
    table->size--;
    return true;
  }

  return false;
}

/**
   Resize hash table to be larger
 */
static bool
table_bp_expand(table_bp *T)
{
  int new_capacity = (int)(table_bp_expand_factor * (float)T->capacity);
  assert(new_capacity > T->capacity);
  table_bp_entry *new_array = malloc(sizeof(T->array[0]) *
                                     (size_t)new_capacity);

  if (new_array == NULL)
  {
    return false;
  }

  for (int i = 0; i < new_capacity; i++)
  {
    table_bp_clear_entry(&new_array[i]);
  }

  // Rehash and move all entries from old table
  for (int i = 0; i < T->capacity; i++)
  {
    table_bp_entry *head = &T->array[i];
    if (!table_bp_entry_valid(head))
    {
      assert(head->next == NULL);
      continue; // Bucket was empty
    }

    bool is_head;
    table_bp_entry *e, *next;
    for (e = head, is_head = true; e != NULL; e = next, is_head = false)
    {
     // Store right away since e might be modified upon adding to new list
      next = e->next;

      // all entries should be valid if we get here
      assert(table_bp_entry_valid(e));
      int new_ix = binkey_hash(table_bp_get_key(e),
            table_bp_key_len(e), new_capacity);

      // Add at tail of new buckets to preserve insert order.
      // This requires traversing list, but collisions should be fairly
      // few, especially in enlarged table.
      bool ok = bucket_add_tail(&new_array[new_ix], e, is_head);
      if (!ok)
      {
        // TODO: how to handle? we're in an awkward state in the middle
        //  of moving entries.
        //  It's unlikely that this will happen.  It is impossible if
        //  we're doubling the size of the array since no list heads will
        //  be demoted to non-heads
        return false;
      }
    }
  }

  free(T->array);
  T->array = new_array;
  T->capacity = new_capacity;
  T->resize_threshold = calc_resize_threshold(T);
  return true;
}

/** format specifies the output format for the data items
 */
void
table_bp_dump(const char* format, const table_bp* target)
{
  table_bp_dump2(format, target, true);
}

void
table_bp_dumpkeys(const table_bp* target)
{
  table_bp_dump2(NULL, target, false);
}

static void
table_bp_dump2(const char *format, const table_bp* target, bool include_vals)
{
  printf("{\n");
  for (int i = 0; i < target->capacity; i++)
  {
    table_bp_entry *head = &target->array[i];
    if (!table_bp_entry_valid(head))
    {
      // Skip empty buckets
      continue;
    }
    printf("%i: ", i);

    for (table_bp_entry *e = head; e != NULL; e = e->next)
    {
      printf("(");
      binkey_printf(table_bp_get_key(e), table_bp_key_len(e));
      if (include_vals)
      {
        printf(", ");
        if (format == NULL)
        {
          // Print pointer by default
          printf("%p", e->data);
        }
        else
        {
          printf(format, e->data);
        }
      }
      printf(")");

      if (e->next != NULL)
        printf(", ");
    }
    printf("\n");
  }
  printf("}\n");
}

static size_t
bucket_keys_string_length(const table_bp_entry *head)
{
  size_t result = 0;

  // Check for non-empty bucket
  if (table_bp_entry_valid(head))
  {
    for (const table_bp_entry *e = head; e != NULL; e = e->next)
    {
      // Each byte is two hex digits in string repr.
      // Plus separator char
      result += table_bp_key_len(e) * 2 + 1;
    }
  }
  return result;
}

size_t
table_bp_keys_string_length(const table_bp* target)
{
  size_t result = 0;
  for (int i = 0; i < target->capacity; i++)
    // String length plus separator
    result += bucket_keys_string_length(&target->array[i]);
  return result;
}

size_t
table_bp_keys_string(char** result, const table_bp* target)
{
  // Allocate required memory
  size_t length = table_bp_keys_string_length(target);
  *result = malloc(length + 1);
  // Update length with actual usage
  length = table_bp_keys_tostring(*result, target);
  return length;
}

size_t
table_bp_keys_string_slice(char** result,
                        const table_bp* target,
                        int count, int offset)
{
  size_t length = table_bp_keys_string_length(target);
  // Allocate size for each key and a space after each one
  *result = malloc(length + (size_t)(target->size+1));
  // Update length with actual usage
  length = table_bp_keys_tostring_slice(*result, target, count, offset);
  return length;
}

static size_t
bucket_keys_tostring(char *result, const table_bp_entry *head)
{
  if (!table_bp_entry_valid(head)) // Empty bucket
    return 0;
  char *p = result;
  for (const table_bp_entry *e = head; e != NULL; e = e->next)
  {
    // Each byte is two hex digits in string repr.
    p += binkey_sprintf(p, table_bp_get_key(e), table_bp_key_len(e));
    p[0] = ' ';
    p++;
  }
  p[0] = '\0'; // Make sure null terminated
  return (size_t)(p - result);
}

static size_t
bucket_tostring(char *result, size_t size, const char *format,
                const table_bp_entry *head)
{
  if (size <= 2)
  {
    return 0;
  }
  char* p = result;

  p += sprintf(p, "[");

  // Check for empty bucket
  if (table_bp_entry_valid(head))
  {
    for (const table_bp_entry *item = head; item; item = item->next)
    {
      p = bp_append_pair(p, table_bp_get_key(item), table_bp_key_len(item),
                         format, item->data, item->next != NULL);
    }
  }
  p += sprintf(p, "]");

  return (size_t)(p - result);
}

size_t
table_bp_keys_tostring(char* result, const table_bp* target)
{
  char* p = result;
  // Null terminate in case empty
  p[0] = '\0';
  for (int i = 0; i < target->capacity; i++)
    p += bucket_keys_tostring(p, &target->array[i]);
  return (size_t)(p-result);
}

size_t
table_bp_keys_tostring_slice(char* result, const table_bp* target,
                          int count, int offset)
{
  // Count of how many items we have covered
  int c = 0;
  char* p = result;
  p[0] = '\0';
  TABLE_BP_FOREACH(target, item) {
    if (c < offset) {
      c++;
      continue;
    }
    if (c >= offset+count && count != -1)
      break;
    p += binkey_sprintf(p, table_bp_get_key(item),
                      table_bp_key_len(item));
    *(p++) = ' ';
    c++;
  }
  return (size_t)(p-result);
}

/** Dump table_bp to string as in snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    internally allocates O(size) memory
    returns int greater than size if size limits are exceeded
            indicating result is garbage
*/
size_t
table_bp_tostring(char* output, size_t size,
               char* format, const table_bp* target)
{
  size_t error = size+1;
  char* ptr = output;
  int i;
  ptr += sprintf(output, "{\n");

  char* s = malloc(sizeof(char) * size);

  for (i = 0; i < target->capacity; i++)
  {
    size_t r = bucket_tostring(s, size, format, &target->array[i]);
    if (((size_t)(ptr-output)) + r + 2 < size)
      ptr += sprintf(ptr, "%s\n", s);
    else
    {
      free(s);
      return error;
    }
  }
  sprintf(ptr, "}\n");

  free(s);
  return (size_t)(ptr-output);
}
