
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
#include <tree.h>

#include "src/util/debug.h"

#include "cache.h"

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
static struct tree lru;

/** Cache entry */
struct entry
{
  turbine_datum_id td;
  turbine_type type;
  void* data;
  int length;
  /** Counter as of last access */
  long stamp;
};

void
turbine_cache_init(int size, unsigned long max_memory)
{
  DEBUG_CACHE("cache_init");
  assert(!initialized);
  initialized = true;
  table_lp_init(&entries, size);
  tree_init(&lru);
  max_entries = size;
  memory = max_memory;
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
                       turbine_type* type, void** result, int* length)
{
  DEBUG_CACHE("retrieve: <%li>", td);
  struct entry* e = table_lp_search(&entries, td);
  if (e == NULL)
    return TURBINE_ERROR_NOT_FOUND;
  *type   = e->type;
  *result = e->data;
  *length = e->length;

  // Update LRU
  bool b = tree_move(&lru, e->stamp, counter);
  valgrind_assert(b);
  e->stamp = counter;
  counter++;

  DEBUG_CACHE("retrieved");

  return TURBINE_SUCCESS;
}

static inline void cache_add(turbine_datum_id td, turbine_type type,
                             void* data, int length);

static inline void cache_replace(turbine_datum_id td,
                                 turbine_type type,
                                 void* data, int length);

turbine_code
turbine_cache_store(turbine_datum_id td, turbine_type type,
                    void* data, int length)
{
  if (max_entries == 0)
    return TURBINE_SUCCESS;

  DEBUG_CACHE("store: <%li>", td);
  assert(entries.size <= max_entries);
  if (max_entries - entries.size == 1)
  {
    cache_replace(td, type, data, length);
  }
  else
  {
    cache_add(td, type, data, length);
  }

  return TURBINE_SUCCESS;
}

static inline void
entry_init(struct entry* result,
           turbine_datum_id td, turbine_type type,
           void* data, int length, long counter)
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
             void* data, int length, long counter)
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
          void* data, int length)
{
  struct entry* e = entry_create(td, type, data, length, counter);
  table_lp_add(&entries, td, e);
  tree_add(&lru, counter, e);
  counter++;
  memory -= length;
  cache_shrink();
}

/**
   Replace oldest cache entry with this one
   This prevents memory reallocation in the tree
 */
static inline void
cache_replace(turbine_datum_id td, turbine_type type,
              void* data, int length)
{
  // Lookup the least-recently-used entry
  struct tree_node* node = tree_leftmost(&lru);
  struct entry* e = node->data;
  // Remove the victim from cache data structures
  tree_remove_node(&lru, node);
  table_lp_remove(&entries, e->td);
  memory += e->length;
  free(e->data);
  // Replace the entry with the new data
  entry_init(e, td, type, data, length, counter);
  node->key = counter;
  tree_add_node(&lru, node);
  table_lp_add(&entries, td, e);
  memory -= length;
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
    turbine_datum_id td;
    struct entry* e;
    void* v;
    tree_pop(&lru, &td, &v);
    e = (struct entry*) v;
    memory += e->length;
    free(e->data);
    free(e);
  }
}

void
turbine_cache_finalize()
{
  if (!initialized)
    // This process is not an engine/worker
    return;
  DEBUG_CACHE("finalize");
  for (int i = 0; i < entries.capacity; i++)
    for (struct list_lp_item* item = entries.array[i].head; item;
        item = item->next)
    {
      struct entry* e = (struct entry*) item->data;
      free(e->data);
    }
  table_lp_delete(&entries);
  table_lp_release(&entries);
  tree_clear(&lru);
}
