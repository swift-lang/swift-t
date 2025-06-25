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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "strkeys.h"
#include "table.h"
#include "c-utils-types.h"
#include "jenkins-hash.h"

// Double in size for now
static const float table_expand_factor = 2.0;

static void
table_dump2(const char* format, const struct table* target,
               bool include_vals);

static table_entry*
find_bucket(const struct table* T, const char* key, size_t *key_strlen);

static bool
bucket_add_head(table_entry *head, char* key, void* data);

static bool
bucket_add_tail(table_entry *head, table_entry *entry,
                bool copy_entry);

static table_entry*
table_locate_entry(const struct table *T, const char *key,
                   table_entry **prev);

static void
table_remove_entry(table_entry *e, table_entry *prev);

static bool
table_expand(struct table *T);

static size_t
bucket_keys_string_length(const table_entry *head);

static size_t
bucket_keys_tostring(char *result, const table_entry *head);

static size_t
bucket_tostring(char *result, size_t size, const char *format,
                const table_entry *head);

static int
calc_resize_threshold(struct table *T)
{
  return (int)((float)T->capacity * T->load_factor);
}

/**
   Warning: If this function fails, it may have leaked memory.
*/
bool
table_init(struct table* target, int capacity)
{
  return table_init_custom(target, capacity, TABLE_DEFAULT_LOAD_FACTOR);
}

bool
table_init_custom(struct table* target, int capacity, float load_factor)
{
  assert(capacity >= 1);
  target->size     = 0;
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
    table_clear_entry(&target->array[i]);
  }
  return true;
}

struct table*
table_create(int capacity)
{
  return table_create_custom(capacity, TABLE_DEFAULT_LOAD_FACTOR);
}

struct table*
table_create_custom(int capacity, float load_factor)
{
  struct table* new_table =  malloc(sizeof(const struct table));
  if (! new_table)
    return NULL;

  bool result = table_init(new_table, capacity);
  if (!result)
  {
    free(new_table);
    return NULL;
  }

  return new_table;
}

void
table_free(struct table* target)
{
  table_free_callback(target, true, NULL);
}

void table_free_callback(struct table* target, bool free_root,
                         void (*callback)(const char*, void*))
{
  for (int i = 0; i < target->capacity; i++)
  {
    table_entry *head = &target->array[i];
    if (table_entry_valid(head))
    {
      // Store next pointer to allow freeing entry
      bool is_head;
      table_entry *e, *next;
      for (e = head, next = head->next, is_head = true;
           e != NULL;
           e = next, is_head = false)
      {
        next = e->next; // Store next right away

        if (callback != NULL)
          callback(e->key, e->data);

        free(e->key);
        if (!is_head) {
          // head is stored in array, rest were malloced separately
          free(e);
        }
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
table_destroy(struct table* target)
{
  for (int i = 0; i < target->capacity; i++)
  {
    table_entry *head = &target->array[i];
    if (table_entry_valid(head))
    {
      // Store next pointer to allow freeing entry
      bool is_head;
      table_entry *e, *next;
      for (e = head, next = head->next, is_head = true;
           e != NULL;
           e = next, is_head = false)
      {
        next = e->next; // Store next right away

        free(e->key);
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
table_clear(struct table* target)
{
  for (int i = 0; i < target->capacity; i++)
  {
    table_entry *head = &target->array[i];
    if (table_entry_valid(head))
    {
      // Store next pointer to allow freeing entry
      bool is_head;
      table_entry*e, *next;
      for (e = head, next = head->next, is_head = true;
           e != NULL;
           e = next, is_head = false)
      {
        next = e->next; // Store next right away

        if (!is_head)
        {
         free(e);
        }
      }
    }
  }
}

void
table_release(struct table* target)
{
  free(target->array);
  target->array = NULL;
}

/**
  Return the head of the appropriate bucket for the key
  key_strlen: return key string length
 */
static inline table_entry*
find_bucket(const struct table* T, const char* key, size_t *key_strlen)
{
  int index = strkey_hash2(key, T->capacity, key_strlen);
  return &T->array[index];
}

/*
 Add at head
 key: appropriate representation with inlining
 */
static inline bool
bucket_add_head(table_entry *head, char* key, void* data)
{
  if (table_entry_valid(head))
  {
    // Head occupied: replace head with this one
    table_entry *e = malloc(sizeof(table_entry));
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
bucket_add_tail(table_entry *head, table_entry *entry, bool copy_entry)
{
  if (table_entry_valid(head))
  {
    // Head occupied: find tail and add
    if (copy_entry)
    {
      table_entry *new_entry = malloc(sizeof(*entry));
      if (new_entry == NULL)
      {
        return false;
      }
      memcpy(new_entry, entry, sizeof(*entry));
      entry = new_entry;
    }

    table_entry *tail = head; // Find tail of list
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
table_add(struct table* target, const char* key, void* data)
{
  // Check to resize hash table
  if (target->size > target->resize_threshold)
  {
    bool ok = table_expand(target);
    if (!ok)
      return false;
  }

  /*
   * Add at head of list to avoid traversing list.  This means that in
   * case of duplicate keys, the newest is returned
   */
  size_t key_strlen;
  table_entry *head = find_bucket(target, key, &key_strlen);

  char *key_repr = malloc(key_strlen + 1);
  if (key_repr == NULL)
  {
    return false;
  }
  memcpy(key_repr, key, key_strlen + 1);

  bool ok = bucket_add_head(head, key_repr, data);
  if (ok)
  {
    target->size++;
    return true;
  }
  else
  {
    // Free allocated key
    free(key_repr);
    return false;
  }
}

/*
  Find entry in table matching key
  prev: if provided, filled with previous entry
  returns: NULL if not found
 */
static table_entry *
table_locate_entry(const struct table *T, const char *key, table_entry **prev)
{
  table_entry *prev_e = NULL;
  size_t key_strlen;
  table_entry *head = find_bucket(T, key, &key_strlen);
  if (!table_entry_valid(head)) // Empty bucket
    return NULL;

  for (table_entry *e = head; e != NULL; e = e->next)
  {
    if (table_key_match(key, e))
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

char*
table_locate_key(const struct table* T, const char* key)
{
  table_entry* e = table_locate_entry(T, key, NULL);
  if (e == NULL) return NULL;
  return e->key;
}

/**
   If found, caller is responsible for old_value -
          it was provided by the user
   @return True if found
 */
bool
table_set(struct table* target, const char* key,
          void* value, void** old_value)
{
  table_entry *e = table_locate_entry(target, key, NULL);
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
table_search(const struct table* table, const char* key,
             void **value)
{
  table_entry *e = table_locate_entry(table, key, NULL);

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
table_contains(const struct table* table, const char* key)
{
  void* tmp = NULL;
  return table_search(table, key, &tmp);
}

/*
 Unlink and free entry if needed
 prev: previous entry, or NULL if list head
 */
static void
table_remove_entry(table_entry *e, table_entry *prev)
{
  if (prev == NULL)
  {
    // Removing head of list
    if (e->next == NULL)
    {
      // List is now empty - reset
      table_clear_entry(e);
    }
    else
    {
      // Promote other entry to head
      table_entry *next = e->next;
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
table_remove(struct table* table, const char* key, void** data)
{
  table_entry *prev;
  table_entry *e = table_locate_entry(table, key, &prev);
  if (e != NULL)
  {
    *data = e->data; // Store data for caller
    free(e->key);

    table_remove_entry(e, prev);
    table->size--;
    return true;
  }

  return false;
}

/**
   Resize hash table to be larger
 */
static bool
table_expand(struct table *T)
{
  int new_capacity = (int)(table_expand_factor * (float)T->capacity);
  assert(new_capacity > T->capacity);
  table_entry *new_array = malloc(sizeof(T->array[0]) *
                                     (size_t)new_capacity);

  if (new_array == NULL)
  {
    return false;
  }

  for (int i = 0; i < new_capacity; i++)
  {
    table_clear_entry(&new_array[i]);
  }

  // Rehash and move all entries from old table
  for (int i = 0; i < T->capacity; i++)
  {
    table_entry *head = &T->array[i];
    if (!table_entry_valid(head))
    {
      assert(head->next == NULL);
      continue; // Bucket was empty
    }

    bool is_head;
    table_entry *e, *next;
    for (e = head, is_head = true; e != NULL; e = next, is_head = false)
    {
     // Store right away since e might be modified upon adding to new list
      next = e->next;

      // all entries should be valid if we get here
      assert(table_entry_valid(e));
      size_t key_strlen;
      int new_ix = strkey_hash2(e->key, new_capacity, &key_strlen);

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
table_dump(const char* format, const struct table* target)
{
  table_dump2(format, target, true);
}

void
table_dumpkeys(const struct table* target)
{
  table_dump2(NULL, target, false);
}

static void
table_dump2(const char *format, const struct table* target, bool include_vals)
{
  printf("{\n");
  for (int i = 0; i < target->capacity; i++)
  {
    table_entry *head = &target->array[i];
    if (!table_entry_valid(head))
    {
      // Skip empty buckets
      continue;
    }
    printf("%i: ", i);

    for (table_entry *e = head; e != NULL; e = e->next)
    {
      printf("(");
      printf("%s", e->key);
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
bucket_keys_string_length(const table_entry *head)
{
  size_t result = 0;

  // Check for non-empty bucket
  if (table_entry_valid(head))
  {
    for (const table_entry *e = head; e != NULL; e = e->next)
    {
      // Each byte is two hex digits in string repr.
      // Plus separator char
      result += strlen(e->key);
    }
  }
  return result;
}

size_t
table_keys_string_length(const struct table* target)
{
  size_t result = 0;
  for (int i = 0; i < target->capacity; i++)
    result += bucket_keys_string_length(&target->array[i]);
  return result;
}

size_t
table_keys_string(char** result, const struct table* target)
{
  size_t length = table_keys_string_length(target);
  // Allocate size for each key and a space after each one
  *result = malloc(length + (size_t)(target->size+1));
  // Update length with actual usage
  length = table_keys_tostring(*result, target);
  return length;
}

size_t
table_keys_string_slice(char** result,
                        const struct table* target,
                        int count, int offset)
{
  size_t length = table_keys_string_length(target);
  // Allocate size for each key and a space after each one
  *result = malloc(length + (size_t)(target->size+1));
  // Update length with actual usage
  length = table_keys_tostring_slice(*result, target, count, offset);
  return length;
}

static size_t
bucket_keys_tostring(char *result, const table_entry *head)
{
  if (!table_entry_valid(head)) // Empty bucket
    return 0;
  char *p = result;
  for (const table_entry *e = head; e != NULL; e = e->next)
    p += sprintf(p, "%s ", e->key);
  return (size_t)(p - result);
}

static size_t
bucket_tostring(char *result, size_t size, const char *format,
                const table_entry *head)
{
  if (size <= 2)
  {
    return 0;
  }
  char* p = result;

  p += sprintf(p, "[");

  // Check for empty bucket
  if (table_entry_valid(head))
  {
    for (const table_entry *item = head; item; item = item->next)
    {
      p = strkey_append_pair(p, item->key, format, item->data,
                             item->next != NULL);
    }
  }
  p += sprintf(p, "]");

  return (size_t)(p - result);
}

size_t
table_keys_tostring(char* result, const struct table* target)
{
  char* p = result;
  for (int i = 0; i < target->capacity; i++)
    p += bucket_keys_tostring(p, &target->array[i]);
  return (size_t)(p-result);
}

size_t
table_keys_tostring_slice(char* result, const struct table* target,
                          int count, int offset)
{
  // Count of how many items we have covered
  int c = 0;
  char* p = result;
  p[0] = '\0';
  TABLE_FOREACH(target, item) {
    if (c < offset) {
      c++;
      continue;
    }
    if (c >= offset+count && count != -1)
      break;
    p += sprintf(p, "%s ", item->key);
    c++;
  }
  return (size_t)(p-result);
}

/** Dump table_sp to string as in snprintf()
    size must be greater than 2.
    format specifies the output format for the data items
    internally allocates O(size) memory
    returns int greater than size if size limits are exceeded
            indicating result is garbage
*/
size_t
table_tostring(char* output, size_t size,
               char* format, const struct table* target)
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
