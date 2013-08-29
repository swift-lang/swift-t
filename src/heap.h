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
 *  Simple binary heap.
 *
 *  Implements a min-heap
 *
 *  Tim Armstrong, 2012
 */

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <math.h>
#include <ctype.h>

#define HEAP_LEFT(i) (i * 2 + 1)
#define HEAP_RIGHT(i) (i * 2 + 2)
#define HEAP_PARENT(i) ((i - 1) / 2)

#define heap_min(X, Y) ( (X) < (Y) ? (X) : (Y))
#define heap_max(X, Y) ( (X) > (Y) ? (X) : (Y))

typedef int   heap_key_t;
typedef void* heap_val_t;
typedef size_t heap_ix_t;

typedef struct {
  heap_key_t key;
  heap_val_t val; // can customize this
} heap_entry_t;

typedef struct {
  heap_entry_t *array;
  size_t size;
  size_t malloced_size;
} heap;

// The heap test case uses calls that are checkable at compile
// time, resulting in warnings.  Allow user to disable the assert()s.
#ifndef HEAP_SKIP_ASSERTS
#define HEAP_ASSERT(x) assert(x)
#else
#define HEAP_ASSERT(x) // noop
#endif

static inline void
heap_init(heap *heap, size_t init_capacity)
{
  assert(init_capacity > 0);
  if(!(heap->array = 
          malloc(sizeof(heap_entry_t) * init_capacity))) {
    fprintf(stderr, "Could not malloc\n");
    exit(EXIT_FAILURE);
  }

  heap->size = 0;
  heap->malloced_size = init_capacity;
}

static inline void
heap_clear_callback(heap *heap, void (*cb)(heap_key_t, heap_val_t))
{
  if (cb != NULL)
  {
    for (int i = 0; i < heap->size; i++)
    {
      cb(heap->array[i].key, heap->array[i].val);
    }
  }
  free(heap->array);
  heap->array = NULL;
  heap->size = heap->malloced_size = 0;
}

static inline void
heap_clear(heap *heap)
{
  heap_clear_callback(heap, NULL);
}

static inline heap*
heap_create(size_t init_capacity) {
  heap* result = malloc(sizeof(heap));
  heap_init(result, init_capacity);
  return result;
}

static inline void
heap_check(heap *heap) {
  heap_ix_t i;
  for (i = 0; i < heap->size; i++) {
    heap_key_t k = heap->array[i].key;
    if (HEAP_LEFT(i) < heap->size) {
      heap_key_t kl = heap->array[HEAP_LEFT(i)].key;
      if (kl < k) {
        printf("bad heap: key(%li) == %li, key(%li) == %li\n",
               (long) i, (long) k, (long) HEAP_LEFT(i), (long) kl);
      }
    }
    if (HEAP_RIGHT(i) < heap->size) {
      heap_key_t kr = heap->array[HEAP_RIGHT(i)].key;
      if (kr < k) {
        printf("bad heap: key(%li) == %li, key(%li) == %li\n",
               (long) i, (long) k, (long) HEAP_RIGHT(i), (long) kr);
      }
    }
  }
}

static inline heap_ix_t heap_size(heap *heap) {
  return heap->size;
}

static inline heap_entry_t heap_root(heap *heap) {
  assert(heap->size > 0);
  return heap->array[0];
}

static inline heap_key_t heap_root_key(heap *heap) {
  assert(heap->size > 0);
  return heap->array[0].key;
}

static inline heap_val_t heap_root_val(heap *heap) {
  assert(heap->size > 0);
  return heap->array[0].val;
}


/*
 * Given a heap where everything is heapified except
 * i, which may be greater than its children, then
 * sift down the entry at i.
 */
static inline void heap_sift_down(heap *heap, heap_ix_t i) {
  assert(heap->size > i);
  heap_entry_t entry = heap->array[i];
  heap_key_t key = entry.key;
  while (1) {
    if ( HEAP_LEFT(i) < heap->size ) {
      /* Find the smaller child */
      heap_ix_t min_ix = HEAP_LEFT(i); 
      heap_key_t min = heap->array[HEAP_LEFT(i)].key;
      if ( HEAP_RIGHT(i) < heap->size &&
         heap->array[HEAP_RIGHT(i)].key < min) {
        min = heap->array[HEAP_RIGHT(i)].key;
        min_ix = HEAP_RIGHT(i);
      }

      /* Check if minheap property is violated */
      if (min < key) {
        heap->array[i] = heap->array[min_ix];
        i = min_ix;
      } else {
        /* entry is less than children, we now have a min-heap again */
        heap->array[i] = entry;
        break;
      }
    } else {
      /* At bottom, put in entry and we're done */
      heap->array[i] = entry;
      break;
    }
  }
}

/*
 * Remove the root of the heap and 
 */
static inline void heap_del_root(heap *heap)
{
  /* Shrink by one */
  heap->size--;

  if (heap->size > 0) {
    /* put last element up the top */
    heap->array[0] = heap->array[heap->size];
    heap_sift_down(heap, 0);
  }
}

/* Increase the key of an entry in the heap.  This may cause it to
 * need to be moved down the heap.  Keep on swapping it down until
 * it is resolved */
static inline void heap_increase_key(heap *heap, heap_ix_t i, heap_key_t newkey) {
  assert(heap->size > i);
  heap_entry_t *entry = &(heap->array[i]);

  HEAP_ASSERT(newkey >= entry->key);
  entry->key = newkey;
  heap_sift_down(heap, i);
}

/*
 * Given an almost-heap, where everything has the
 * heap property, except A[i] might have key < its parent.
 * Sift up A[i] until it is a heap again
 */
static inline void heap_sift_up(heap *heap, heap_ix_t i) {
  assert(heap->size > i);
  heap_entry_t entry, parent;
  entry = heap->array[i];
  while (i > 0) {
    parent = heap->array[HEAP_PARENT(i)];
    if (parent.key <= entry.key) {
      /* Is heap */
      break;
    } else {
      heap->array[i] = parent;
      i = HEAP_PARENT(i);
    }
  }
  /* Last i should be the location where new prime belonds */
  heap->array[i] = entry;
}

static inline void
heap_expand(heap *heap, size_t needed_size)
{
  size_t new_size;
  /* Expand the array if needed */
  assert(heap->size <= heap->malloced_size);
  if (heap->malloced_size < needed_size) {
    /* need to expand - expand  1.5x (rounded up) */
    new_size = heap_min(needed_size, ((heap->malloced_size * 3 - 1) / 2) + 1);
    if ( (heap->array = realloc( heap->array, sizeof(heap_entry_t) * new_size))  == NULL) {
      printf("Could not realloc %lu for heap array\n",
             (unsigned long) new_size);
      exit(EXIT_FAILURE);
    }

    heap->malloced_size = new_size;
  }
}

static inline void
heap_add_entry(heap *heap, heap_entry_t entry) {
  /* Make sure big enough */
  heap_expand(heap, heap->size + 1);

  /* Add to end of the array */
  heap->array[heap->size] = entry;
  heap->size++;

  /* Rearrange so it a heap again */
  heap_sift_up(heap, heap->size -1);
}

static inline void
heap_add(heap *heap, heap_key_t k, heap_val_t v)
{
  heap_entry_t entry = {k, v};
  heap_add_entry(heap, entry);
}

/* Decrease the key of an entry in the heap.  This may cause it to
 * need to be moved up the heap. */
static inline void
heap_decrease_key(heap *heap, heap_ix_t i, heap_key_t newkey)
{
  heap_entry_t *entry = &(heap->array[i]);
  HEAP_ASSERT(newkey <= entry->key);
  entry->key = newkey;
  heap_sift_up(heap, i);
}
