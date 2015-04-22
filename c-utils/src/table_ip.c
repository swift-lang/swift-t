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

/*
 * table_ip.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "table_ip.h"
#include "jenkins-hash.h"

// Double in size for now
static const float table_expand_factor = 2.0;

static int
hash_int(int key, int N)
{
  uint32_t p = bj_hashlittle(&key, sizeof(key), 0u);
  return (int)(p % (uint32_t)N);
}

static void
table_ip_dump2(const char* format, const table_ip* target,
               bool include_vals);

static table_ip_bucket*
find_bucket(const table_ip* T, int key);

static bool 
bucket_add_head(table_ip_bucket *b, int key, void* data);

static bool
bucket_add_tail(table_ip_bucket *b, table_ip_entry *entry,
                bool copy_entry);

static table_ip_entry*
bucket_locate_entry(table_ip_bucket *bucket, int key,
                    table_ip_entry **prev);

static void
table_ip_remove_entry(table_ip_bucket *b, table_ip_entry *e,
                      table_ip_entry *prev);

static bool
table_ip_expand(table_ip *T);

static char*
lp_append_pair(char* ptr, int key, char* val_str, bool last);

static size_t
bucket_tostring(char *str, size_t size, const char *format,
                const table_ip_bucket *b);

static int
calc_resize_threshold(table_ip *T)
{
  return (int)((float)T->capacity * T->load_factor);
}

static void table_ip_clear_bucket(table_ip_bucket *bucket)
{
  bucket->valid = false;
}

bool
table_ip_init(table_ip* table, int capacity)
{
  return table_ip_init_custom(table, capacity, TABLE_IP_DEFAULT_LOAD_FACTOR);
}

bool
table_ip_init_custom(struct table_ip* target, int capacity, float load_factor)
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
    table_ip_clear_bucket(&target->array[i]);
  }
  return true;
}

table_ip*
table_ip_create(int capacity)
{
  return table_ip_create_custom(capacity, TABLE_IP_DEFAULT_LOAD_FACTOR);
}

table_ip*
table_ip_create_custom(int capacity, float load_factor)
{
  table_ip *new_table = malloc(sizeof(table_ip));
  if (! new_table)
    return NULL;

  bool result = table_ip_init_custom(new_table, capacity, load_factor);
  if (!result)
  {
    free(new_table);
    return NULL;
  }

  return new_table;
}

void
table_ip_clear(table_ip* target)
{
  for (int i = 0; i < target->capacity; i++)
  {
    table_ip_bucket *b = &target->array[i];
    if (table_ip_bucket_valid(b))
    {
      bool is_head;
      table_ip_entry *e, *next;
      for (e = &b->head, is_head = true; e != NULL;
           e = next, is_head = false) {
        next = e->next;
        
        if (!is_head)
          free(e);
      }
      b->valid = false;
    }
  }
  target->size = 0; 
}

void
table_ip_delete(table_ip* target)
{
  for (int i = 0; i < target->capacity; i++)
  {
    table_ip_bucket *b = &target->array[i];
    if (table_ip_bucket_valid(b))
    {
      bool is_head;
      table_ip_entry *e, *next;
      for (e = &b->head, is_head = true; e != NULL;
           e = next, is_head = false) {
        next = e->next;
        free(e->data);
        if (!is_head)
          free(e);
      }
      b->valid = false;
    }
  }
  target->size = 0; 
}

void
table_ip_destroy(table_ip* target)
{
  table_ip_free_callback(target, true, NULL);
}
void
table_ip_free(table_ip* target)
{
  table_ip_free_callback(target, true, NULL);
}

void table_ip_free_callback(table_ip* target, bool free_root,
                            void (*callback)(int, void*))
{
  if (callback == NULL)
  {
    table_ip_clear(target);
  }
  else
  {
    for (int i = 0; i < target->capacity; i++)
    {
      table_ip_bucket *b = &target->array[i];
      if (table_ip_bucket_valid(b))
      {
        bool is_head;
        table_ip_entry *e, *next;
        for (is_head = true, e = &b->head; e != NULL;
             e = next, is_head = false) {
          next = e->next;
          callback(e->key, e->data);
          if (!is_head)
            free(e);
        }
        b->valid = false;
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
    target->size = target->capacity = 0;
  }
}

void
table_ip_release(table_ip* target)
{
  free(target->array);
}

static table_ip_bucket*
find_bucket(const table_ip* T, int key)
{
  int index = hash_int(key, T->capacity);
  return &T->array[index];
}

static bool 
bucket_add_head(table_ip_bucket *b, int key, void* data)
{
  if (table_ip_bucket_valid(b))
  {
    // Head occupied: replace head with this one
    table_ip_entry *e = malloc(sizeof(table_ip_entry));
    if (e == NULL)
    {
      return false;
    }
    // Copy current head to e
    memcpy(e, &b->head, sizeof(*e));
    b->head.next = e;
  }
  else
  {
    b->valid = true;
    b->head.next = NULL;
  }

  // Put new data in head
  b->head.key = key;
  b->head.data = data;
  return true;
}

static bool
bucket_add_tail(table_ip_bucket *b, table_ip_entry *entry,
                bool copy_entry)
{
  if (table_ip_bucket_valid(b))
  {
    // Head occupied: find tail and add
    if (copy_entry)
    {
      table_ip_entry *new_entry = malloc(sizeof(*entry));
      if (new_entry == NULL)
      {
        return false;
      }
      memcpy(new_entry, entry, sizeof(*entry));
      entry = new_entry;
    }

    table_ip_entry *tail = &b->head; // Find tail of list
    while (tail->next != NULL)
    {
      tail = tail->next;
    }
    tail->next = entry;
    entry->next = NULL;
  }
  else
  {
    b->valid = true;

    // Empty list case
    memcpy(&b->head, entry, sizeof(b->head));
    b->head.next = NULL;

    if (!copy_entry)
    {
      // Don't need memory since we copied to head
      free(entry);
    }
  }

  return true;
}

int
table_ip_size(table_ip* target)
{
  return target->size;
}

/**
   @return true on success, false on failure (memory)
 */
bool
table_ip_add(table_ip *target, int key, void* data)
{
  // Check to resize hash table
  if (target->size > target->resize_threshold)
  {
    bool ok = table_ip_expand(target);
    if (!ok)
      return false;
  }

  /* 
   * Add at head of list to avoid traversing list.  This means that in
   * case of duplicate keys, the newest is returned
   */
  table_ip_bucket *head = find_bucket(target, key);

  bool ok = bucket_add_head(head, key, data);
  if (ok)
  {
    target->size++;
    return true;
  }
  else
  {
    return false;
  }
}

/*
  Find entry in table matching key
  prev: if provided, filled with previous entry
  returns: NULL if not found
 */
static table_ip_entry*
bucket_locate_entry(table_ip_bucket *bucket, int key,
                    table_ip_entry **prev)
{
  table_ip_entry *prev_e = NULL;
  if (!table_ip_bucket_valid(bucket)) // Empty bucket
    return NULL;

  for (table_ip_entry *e = &bucket->head; e != NULL; e = e->next)
  {
    if (key == e->key)
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

bool
table_ip_search(table_ip* table, int key, void **value)
{
  table_ip_bucket *b = find_bucket(table, key);
  table_ip_entry *e = bucket_locate_entry(b, key, NULL);

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
table_ip_contains(table_ip* table, int key)
{
  void* tmp = NULL;
  return table_ip_search(table, key, &tmp);
}

/*
 Unlink and free entry if needed
 prev: previous entry, or NULL if list head
 */
static void
table_ip_remove_entry(table_ip_bucket *b, table_ip_entry *e,
                      table_ip_entry *prev)
{
  if (prev == NULL)
  {
    // Removing head of list
    if (e->next == NULL)
    {
      // List is now empty - reset
      table_ip_clear_bucket(b);
    }
    else
    {
      // Promote other entry to head
      table_ip_entry *next = e->next;
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
table_ip_move(table_ip* table, int key_old, int key_new)
{
  void *val;

  if (!table_ip_remove(table, key_old, &val))
  {
    return false;
  }
  return table_ip_add(table, key_new, val);
}

bool
table_ip_remove(table_ip* table, int key, void **value)
{
  table_ip_entry *prev;
  table_ip_bucket *b = find_bucket(table, key);
  table_ip_entry *e = bucket_locate_entry(b, key, &prev);
  if (e != NULL)
  {
    *value = e->data; // Store data for caller

    table_ip_remove_entry(b, e, prev); 
    table->size--;
    return true;
  }
  return false;
}

/**
   Resize hash table to be larger
 */
static bool
table_ip_expand(table_ip *T)
{
  int new_capacity = (int)(table_expand_factor * (float)T->capacity);
  assert(new_capacity > T->capacity);
  table_ip_bucket *new_array = malloc(sizeof(T->array[0]) *
                                     (size_t)new_capacity);

  if (new_array == NULL)
  {
    return false;
  }
  
  for (int i = 0; i < new_capacity; i++)
  {
    table_ip_clear_bucket(&new_array[i]);
  }
  
  // Rehash and move all entries from old table
  for (int i = 0; i < T->capacity; i++)
  {
    table_ip_bucket *b = &T->array[i];
    if (!table_ip_bucket_valid(b))
    {
      continue; // Bucket was empty
    }

    bool is_head;
    table_ip_entry *e, *next;
    for (e = &b->head, is_head = true; e != NULL; e = next, is_head = false)
    {
     // Store right away since e might be modified upon adding to new list
      next = e->next;

      int new_ix = hash_int(e->key, new_capacity);

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
table_ip_dump(const char* format, const table_ip* target)
{
  table_ip_dump2(format, target, true);
}

void
table_ip_dumpkeys(const table_ip* target)
{
  table_ip_dump2(NULL, target, false);
}

static void
table_ip_dump2(const char *format, const table_ip* target, bool include_vals)
{
  printf("{\n");
  for (int i = 0; i < target->capacity; i++)
  {
    table_ip_bucket *b = &target->array[i];
    if (!table_ip_bucket_valid(b))
    {
      // Skip empty buckets
      continue;
    }
    printf("%i: ", i);

    for (table_ip_entry *e = &b->head; e != NULL; e = e->next)
    {
      printf("(");
      printf("%i", e->key);
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

static char*
lp_append_pair(char* ptr, int key, char* val_str, bool last)
{
  ptr += sprintf(ptr, "(%i,", key);
  ptr += sprintf(ptr, "%s)", val_str);

  if (!last)
    ptr += sprintf(ptr, ",");
  return ptr;
}

static size_t
bucket_tostring(char *str, size_t size, const char *format,
                const table_ip_bucket *b)
{
  size_t error = size+1;
  char* ptr   = str;

  if (size <= 2)
    return error;

  ptr += sprintf(ptr, "[");

  for (const struct table_ip_entry* e = &b->head; e != NULL;
       e = e->next)
  {
    char* s;
    int r = asprintf(&s, format, e->data);
    if ((ptr-str) + 10 + r + 4 < size)
    {
      ptr = lp_append_pair(ptr, e->key, s, e->next == NULL);
      free(s);
    }
    else
    {
      free(s);
      return error;
    }
  }
  ptr += sprintf(ptr, "]");

  return (size_t)(ptr-str);

}

/** Dump to string a la snprintf()
        size must be greater than 2.
        format specifies the output format for the data items
        internally allocates O(size) memory
        returns int greater than size if size limits are exceeded
                indicating result is garbage
 */
size_t table_ip_tostring(char* str, size_t size,
                    char* format, table_ip* target)
{
  size_t error = size+1;
  char* ptr   = str;
  int i;
  ptr += sprintf(str, "{\n");

  char* s = (char*) malloc(sizeof(char) * size);

  for (i = 0; i < target->size; i++)
  {
    size_t r = bucket_tostring(s, size, format, &target->array[i]);
    if ((size_t)(ptr-str) + r + 2 < size)
    {
      int len = sprintf(ptr, "%s\n", s);
      assert(len > 0);
      ptr += (size_t)len;
    }
    else
    {
      free(s);
      return error;
    }
  }
  sprintf(ptr, "}\n");

  free(s);
  return (size_t)(ptr-str);
}
