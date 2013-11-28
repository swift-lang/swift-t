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

#include "binkeys.h"

#include "table_bp.h"
#include "c-utils-types.h"


// Double in size for now
static const float table_bp_expand_factor = 2.0;

static void
table_bp_dump2(const char* format, const table_bp* target, bool include_vals);

static bool table_bp_item_add_head(table_bp_item *head,
            void* key, size_t key_len, void* data);

static bool table_bp_item_add_tail(table_bp_item *head,
            table_bp_item *entry, bool copy_entry);

static void
table_bp_remove_entry(table_bp_entry *e, table_bp_entry *prev);

static bool
table_bp_expand(table_bp *T);

static void init_entry(table_bp_entry *entry)
{
  entry->key = NULL;
  entry->key_len = 0;
  entry->data = NULL;
  entry->next = NULL;
}

static void
update_resize_threshold(table_bp *T)
{
  T->resize_threshold = (int)(capacity * load_factor);
}

/**
   
*/
bool
table_bp_init(table_bp* target, int capacity, float load_factor)
{
  assert(capacity >= 1);
  target->size = 0;
  target->capacity = capacity;
  target->load_factor = load_factor;
  update_resize_threshold(&target);

  target->array = malloc(sizeof(target->array[0]) * (size_t)capacity);
  if (!target->array)
  {
    return false;
  }

  for (int i = 0; i < capacity; i++)
  {
    init_entry(&target->array[i]);
  }
  return true;
}

table_bp*
table_bp_create(int capacity)
{
  table_bp* new_table =  malloc(sizeof(const table_bp));
  if (! new_table)
    return NULL;

  bool result = table_bp_init(new_table, capacity);
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
                         void (*callback)(void*, size_t, void*))
{
  for (int i = 0; i < target->capacity; i++)
  {
    bool head = true;
    for (table_bp_entry *e = &target->array[i]; e != NULL; e = e->next)
    {
      if (e->key != NULL && callback != NULL)
        callback(e->key, e->key_len, e->data);

      if (!head) {
        // head is stored in array, rest were malloced separately
        free(e);
      }
      head = false;
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
    bool head = true;
    for (table_bp_entry *e = &target->array[i]; e != NULL; e = e->next)
    {
      if (e->key != NULL)
      {
        // Entry was valid
        free(item->key);
        free(item->data);
        item->key = item->data = NULL;
      }
      if (!head)
      {
       free(e);
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


static bool table_bp_item_add_head(table_bp_item *head,
            void* key, size_t key_len, void* data)
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
  head->key_len = key_len;
  head->data = data;
  return true;
}

/**
  Add at end of bucket chain
  copy_entry: if true, allocate new entry,
              otherwise reuse provided entry or free
              
 */
static bool table_bp_item_add_tail(table_bp_item *head,
            table_bp_item *entry, bool copy_entry)
{
  if (table_bp_entry_valid(head))
  {
    // Head occupied: find tail and add
    if (copy_entry)
    {
      table_bp_item *new_entry = malloc(sizeof(*entry));
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
    memcpy(head, e, sizeof(*head));
    head->next = NULL;

    if (!copy_entry)
    {
      // Don't need memory since we copied to head
      free(e);
    }
  }

  return true;
}

/**
   Note: duplicates internal copy of key (in list_bp_add())
 */
bool
table_bp_add(table_bp *target, const void* key, size_t key_len,
             void* data)
{
  // Check to resize hash table
  if (target->size >= target->resize_threshold)
  {
    bool ok = table_bp_expand(target);
    if (!ok)
      return false;
  }
  int index = bin_key_hash(key, key_len, target->capacity);

  /* 
   * Add at head of list to avoid traversing list.  This means that in
   * case of duplicate keys, the newest is returned
   */
  table_bp_entry *head = &target->array[index];


  void *key_copy = malloc(key_len);
  if (key_copy == NULL)
  {
    return false;
  }
  memcpy(key_copy, key, key_len);

  bool ok = table_bp_item_add_head(head, key_copy, key_len, data);
  if (ok)
  {
    target->size++;
    return true;
  }
  else
  {
    free(key_copy);
    return false;
  }
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
  int index = bin_key_hash(key, key_len, target->capacity);

  for (table_bp_entry *e = &target->array[index]; e != NULL; e = e->next)
  {
    if (table_bp_key_match(key, key_len, e))
    {
      *old_value = e->data;
      e->data = value;
      return true;
    }
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
  int index = bin_key_hash(key, key_len, table->capacity);

  for (table_bp_entry *e = &target->array[index]; e != NULL; e = e->next)
  {
    if (table_bp_key_match(key, key_len, e)) {
      *value = e->data;
      return true;
    }

  *value = NULL;
  return false;
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
      init_entry(e);
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
  int index = bin_key_hash(key, key_len, table->capacity);
  table_bp_entry *head = &target->array[index];
  table_bp_entry *prev = NULL;
  for (table_bp_entry *e = head; e != NULL; e = e->next)
  {
    if (table_bp_key_match(key, key_len, e)) {
      *data = e->data; // Store data for caller
      free(e->key);

      table_bp_remove_entry(e, prev); 
      table->size--;
      return true;
    }
    prev = e;
  }
  return false;
}

/**
   Resize hash table to be larger
 */
static bool
table_bp_expand(table_bp *T)
{
  int new_capacity = table_bp_expand_factor * T->capacity;
  assert(new_capacity > T->capacity);
  table_bp_entry *new_array = malloc(sizeof(T->array[0]) *
                                     (size_t)new_capacity);

  if (new_array == NULL)
  {
    return false;
  }
  
  
  for (int i = 0; i < new_capacity; i++)
  {
    init_entry(&new_array[i]);
  }
  
  // Rehash and move all entries from old table
  for (int i = 0; i < target->capacity; i++)
  {
    table_bp_entry *head = &target->array[i];
    if (!table_bp_entry_valid(head))
    {
      assert(head->next == NULL);
      continue; // Bucket was empty
    }

    bool is_head = true;
    for (table_bp_entry *e = head; e != NULL; e = e->next)
    {
      // all entries should be valid if we get here
      assert(table_bp_entry_valid(e));
      int new_ix = bin_key_hash(key, key_len, new_capacity);
      bool ok = table_bp_item_add_tail(&new_array[new_ix], e, is_head);
      if (!ok)
      {
        // TODO: how to handle? we're in an awkward state in the middle
        //  of moving entries.
        //  It's unlikely that this will happen.  It is impossible if
        //  we're doubling the size of the array since we don't need to
        //  allocate any memory then.
        return false;
      }

      is_head = false;
    }
  }
  
  free(T->array);
  T->array = new_array;
  T->capacity = new_capacity;
  update_resize_threshold(T); 
  return true;
}

/** format specifies the output format for the data items
  TODO: format ignored
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
  printf("{\n");
  for (int i = 0; i < target->capacity; i++)
  {
    table_bp_entry *head = &target->array[i];
    if (!table_bp_entry_valid(head) && head->next == NULL)
    {
      // Skip empty buckets
      continue;
    }
    printf("%i: ", i);

    for (table_bp_entry *e = head; e != NULL; e = e->next)
    {
      printf("(");
      printf_key(e->key, e->key_len);
      if (include_vals)
      {
        printf(", %s)", (char*) data);
      }
      else
      {
        printf(")");
      }
      if (e->next != NULL)
        printf(", ");
    }
    printf("\n");
  }
  printf("}\n");
}


size_t
table_bp_keys_string_length(const table_bp* target)
{
  size_t result = 0;
  for (int i = 0; i < target->capacity; i++)
    result += list_bp_keys_string_length(target->array[i]);
  return result;
}

size_t
table_bp_keys_string(char** result, const table_bp* target)
{
  size_t length = table_bp_keys_string_length(target);
  // Allocate size for each key and a space after each one
  *result = malloc(length + (size_t)(target->size+1));
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

size_t
table_bp_keys_tostring(char* result, const table_bp* target)
{
  char* p = result;
  for (int i = 0; i < target->capacity; i++)
    p += list_bp_keys_tostring(p, target->array[i]);
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
  for (int i = 0; i < target->capacity; i++)
  {
    struct list_bp* L = target->array[i];
    for (struct list_bp_item* item = L->head; item; item = item->next)
    {
      if (c < offset) {
        c++;
        continue;
      }
      if (c >= offset+count && count != -1)
        break;
      p += sprintf_key(p, item->key, item->key_len);
      *(p++) = ' ';
      c++;
    }
  }
  return (size_t)(p-result);
}

/** Dump list_bp to string as in snprintf()
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
  size_t   error = size+1;
  char* ptr   = output;
  int i;
  ptr += sprintf(output, "{\n");

  char* s = malloc(sizeof(char) * size);

  for (i = 0; i < target->capacity; i++)
  {
    size_t r = list_bp_tostring(s, size, format, target->array[i]);
    if (((size_t)(ptr-output)) + r + 2 < size)
      ptr += sprintf(ptr, "%s\n", s);
    else
      return error;
  }
  sprintf(ptr, "}\n");

  free(s);
  return (size_t)(ptr-output);
}
