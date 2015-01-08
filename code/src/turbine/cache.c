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
 * cache.c
 *
 *  Created on: Sep 4, 2012
 *      Author: wozniak
 */

#include <assert.h>

#include <stdio.h>
#include <table_lp.h>
#include <tools.h>
#include <rbtree.h>

#include "src/util/debug.h"

#include "cache.h"

/**
   Maximal number of cache entries.
   If this is 0, the cache is disabled
 */
static int max_entries;

static bool initialized = false;

/** Avoid relying on clock for LRU */
static long counter = 0;

/** Memory remaining */
static unsigned long memory;

/**
   Cache entries
   Maps from TD to entry
  */
static struct table_lp entries;

/**
   Maintain LRU ordering
   Maps from counter stamp to entry
   Older entries have lower counter stamps; they will be evicted first
 */
static struct rbtree lru;

/** Cache entry */
struct entry
{
  turbine_datum_id td;
  turbine_type type;
  void* data;
  size_t length;
  /** Counter as of last access */
  long stamp;
};

void
turbine_cache_init(int size, unsigned long max_memory)
{
  DEBUG_CACHE("cache_init");
  assert(!initialized);
  initialized = true;
  max_entries = size;
  memory = max_memory;
  if (max_entries == 0)
    return;
  table_lp_init(&entries, size);
  rbtree_init(&lru);

}

bool
turbine_cache_check(turbine_datum_id td)
{
  if (max_entries == 0)
    return false;

  bool result = table_lp_contains(&entries, td);
  DEBUG_CACHE("check: <%li> %s", td,
              result ? "hit" : "miss");
  return result;
}

turbine_code
turbine_cache_retrieve(turbine_datum_id td,
                       turbine_type* type,
                       void** result, size_t* length)
{
  // We do not need to check max_entries here: if max_entries==0,
  // then turbine_cache_check() will miss

  DEBUG_CACHE("retrieve: <%li>", td);
  struct entry* e;
  bool found = table_lp_search(&entries, td, (void**)&e);
  if (!found)
    return TURBINE_ERROR_NOT_FOUND;
  *type   = e->type;
  *result = e->data;
  *length = e->length;

  // Update LRU
  bool b = rbtree_move(&lru, e->stamp, counter);
  valgrind_assert(b);
  e->stamp = counter;
  counter++;

  DEBUG_CACHE("retrieved");

  return TURBINE_SUCCESS;
}

static inline void cache_add(turbine_datum_id td, turbine_type type,
                             void* data, size_t length);

static inline void cache_replace(turbine_datum_id td,
                                 turbine_type type,
                                 void* data, size_t length);

turbine_code
turbine_cache_store(turbine_datum_id td, turbine_type type,
                    void* data, size_t size)
{
  if (max_entries == 0)
    return TURBINE_SUCCESS;

  DEBUG_CACHE("store: <%li> size: %i counter: %li",
              td, size, counter);
  assert(entries.size <= max_entries);
  if (max_entries - entries.size == 1)
  {
    cache_replace(td, type, data, size);
  }
  else
  {
    cache_add(td, type, data, size);
  }

  return TURBINE_SUCCESS;
}

static inline void
entry_init(struct entry* result,
           turbine_datum_id td, turbine_type type,
           void* data, size_t length, long counter)
{
  result->td = td;
  result->type = type;
  result->data = data;
  result->length = length;
  result->stamp = counter;
}

/**
   Allocate and initialize an entry
 */
static inline struct entry*
entry_create(turbine_datum_id td, turbine_type type,
             void* data, size_t length, long counter)
{
  struct entry* result = malloc(sizeof(struct entry));
  entry_init(result, td, type, data, length, counter);
  return result;
}

static inline void cache_shrink(void);

/**
   Add a cache entry- no eviction necessary unless out of memory
*/
static inline void
cache_add(turbine_datum_id td, turbine_type type,
          void* data, size_t length)
{
  assert(length >= 0);
  struct entry* e = entry_create(td, type, data, length, counter);
  table_lp_add(&entries, td, e);
  rbtree_add(&lru, counter, e);
  counter++;
  memory -= (unsigned long)length;
  cache_shrink();
}

/**
   Replace oldest cache entry with this one
   This prevents memory reallocation in the tree
 */
static inline void
cache_replace(turbine_datum_id td, turbine_type type,
              void* data, size_t length)
{
  // Lookup the least-recently-used entry
  struct rbtree_node* node = rbtree_leftmost(&lru);
  struct entry* e = node->data;
  DEBUG_CACHE("cache_replace(): LRU victim: <%li>", e->td);
  // Remove the victim from cache data structures
  rbtree_remove_node(&lru, node);

  void *tmp;
  table_lp_remove(&entries, e->td, &tmp);
  assert(tmp == e);

  assert(e->length >= 0);
  memory += (unsigned long)e->length;
  free(e->data);
  // Replace the entry with the new data
  entry_init(e, td, type, data, length, counter);
  node->key = counter;
  rbtree_add_node(&lru, node);
  table_lp_add(&entries, td, e);
  memory -= (unsigned long)length;
  counter++;
  cache_shrink();
}

/**
  After a cache update, we may be over-allocated on memory
  Shrink the cache until we are under budget again
 */
static inline void
cache_shrink(void)
{
  while (memory < 0)
  {
    int64_t stamp;
    void* v;
    rbtree_pop(&lru, &stamp, &v);
    struct entry* e = (struct entry*) v;
    DEBUG_CACHE("cache_shrink(): LRU victim: <%li>", e->td);
    memory += (unsigned long)e->length;
    free(e->data);
    free(e);
  }

  DEBUG_CACHE("cache_shrink(): memory: %li", memory);
}

void
turbine_cache_finalize()
{
  if (!initialized)
    // This process is not a worker
    return;
  DEBUG_CACHE("finalize");
  TABLE_LP_FOREACH(&entries, item)
  {
    struct entry* e = (struct entry*) item->data;
    free(e->data);
  }
  table_lp_delete(&entries);
  table_lp_release(&entries);
  rbtree_clear(&lru);
  initialized = false;
}
