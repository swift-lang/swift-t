/*
 * Copyright 2013-2014 University of Chicago and Argonne National Laboratory
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
 *  Generic binary heap.
 *
 *  Implements a min-heap.
 *
 *  A specific version can be instantiated by including this header
 *  while setting macros:
 *  HEAP_KEY_T: a numeric key type
 *  HEAP_VAL_T: a value type
 *  HEAP_PFX: the prefix to apply to function names, e.g. my_heap_
 *
 *  This will define types ${HEAP_PFX}key_t ${HEAP_PFX}val_t, and
 *   ${HEAP_PFX}entry_t and a range of heap functions, e.g.
 *   ${HEAP_PFX}init, ${HEAP_PFX}add, etc.
 *
 *  Tim Armstrong, 2012-2014
 */

#include <stdbool.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <assert.h>
#include <math.h>
#include <ctype.h>

#include <tools.h>

#define HEAP_LEFT(i) (i * 2 + 1)
#define HEAP_RIGHT(i) (i * 2 + 2)
#define HEAP_PARENT(i) ((i - 1) / 2)

#define heap_min(X, Y) ( (X) < (Y) ? (X) : (Y))
#define heap_max(X, Y) ( (X) > (Y) ? (X) : (Y))

#define HEAP_MIN_SIZE 16

/*
  Construct specific names for this special heap.
  Preprocessor voodoo with three layers of macros was needed to
  paste tokens together correctly.
 */
#define HEAP_NAME__(prefix, name) prefix ## name
#define HEAP_NAME_(prefix, name) HEAP_NAME__(prefix, name)
#define HEAP_NAME(name) HEAP_NAME_(HEAP_PFX, name)

#ifndef __HEAP_IDX_T_DEFINED
#define __HEAP_IDX_T_DEFINED
typedef uint32_t heap_idx_t;
#endif

typedef HEAP_KEY_T HEAP_NAME(key_t);
typedef HEAP_VAL_T HEAP_NAME(val_t);

#define HEAP_ENTRY_T HEAP_NAME(entry_t)
typedef struct {
  HEAP_KEY_T key;
  HEAP_VAL_T val; // can customize this
} HEAP_ENTRY_T;

#define HEAP_T HEAP_NAME(t)
typedef struct {
  HEAP_ENTRY_T *array;
  uint32_t size;
  uint32_t malloced_size;
} HEAP_T;

// The heap test case uses calls that are checkable at compile
// time, resulting in warnings.  Allow user to disable the assert()s.
#ifndef HEAP_SKIP_ASSERTS
#define HEAP_ASSERT(x) assert(x)
#else
#define HEAP_ASSERT(x) // noop
#endif

#define HEAP_INIT HEAP_NAME(init)
static inline bool
HEAP_INIT(HEAP_T *heap, uint32_t init_capacity)
{
  if (init_capacity == 0)
  {
    heap->array = NULL;
  }
  else
  {
    heap->array =
            malloc(sizeof(HEAP_ENTRY_T) * init_capacity);
    if (heap->array == NULL) {
      return false;
    }
  }

  heap->size = 0;
  heap->malloced_size = init_capacity;
  return true;
}

#define HEAP_INIT_EMPTY HEAP_NAME(init_empty)
static inline bool
HEAP_INIT_EMPTY(HEAP_T *heap)
{
  return HEAP_INIT(heap, 0);
}

#define HEAP_CLEAR_CALLBACK HEAP_NAME(clear_callback)
static inline void
HEAP_CLEAR_CALLBACK(HEAP_T *heap, void (*cb)(HEAP_KEY_T, HEAP_VAL_T))
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

#define HEAP_CLEAR HEAP_NAME(clear)
static inline void
HEAP_CLEAR(HEAP_T *heap)
{
  HEAP_CLEAR_CALLBACK(heap, NULL);
}

#define HEAP_CREATE HEAP_NAME(create)
static inline HEAP_T*
HEAP_CREATE(uint32_t init_capacity) {
  HEAP_T* result = malloc(sizeof(HEAP_T));
  HEAP_INIT(result, init_capacity);
  return result;
}

#define HEAP_CHECK HEAP_NAME(check)
static inline void
HEAP_CHECK(HEAP_T *heap) {
  heap_idx_t i;
  for (i = 0; i < heap->size; i++) {
    HEAP_KEY_T k = heap->array[i].key;
    if (HEAP_LEFT(i) < heap->size) {
      HEAP_KEY_T kl = heap->array[HEAP_LEFT(i)].key;
      if (kl < k) {
        printf("bad heap: key(%li) == %li, key(%li) == %li\n",
               (long) i, (long) k, (long) HEAP_LEFT(i), (long) kl);
      }
    }
    if (HEAP_RIGHT(i) < heap->size) {
      HEAP_KEY_T kr = heap->array[HEAP_RIGHT(i)].key;
      if (kr < k) {
        printf("bad heap: key(%li) == %li, key(%li) == %li\n",
               (long) i, (long) k, (long) HEAP_RIGHT(i), (long) kr);
      }
    }
  }
}

#define HEAP_SIZE HEAP_NAME(size)
static inline heap_idx_t HEAP_SIZE(HEAP_T *heap) {
  return heap->size;
}

#define HEAP_ROOT HEAP_NAME(root)
static inline HEAP_ENTRY_T HEAP_ROOT(HEAP_T *heap) {
  assert(heap->size > 0);
  return heap->array[0];
}

#define HEAP_ROOT_KEY HEAP_NAME(root_key)
static inline HEAP_KEY_T HEAP_ROOT_KEY(HEAP_T *heap) {
  assert(heap->size > 0);
  return heap->array[0].key;
}

#define HEAP_ROOT_VAL HEAP_NAME(root_val)
static inline HEAP_VAL_T HEAP_ROOT_VAL(HEAP_T *heap) {
  assert(heap->size > 0);
  return heap->array[0].val;
}

/*
 * Given a heap where everything is heapified except
 * i, which may be greater than its children, then
 * sift down the entry at i.
 */
#define HEAP_SIFT_DOWN HEAP_NAME(sift_down)
static inline void HEAP_SIFT_DOWN(HEAP_T *heap, heap_idx_t i) {
  assert(heap->size > i);
  HEAP_ENTRY_T entry = heap->array[i];
  HEAP_KEY_T key = entry.key;
  while (1) {
    if ( HEAP_LEFT(i) < heap->size ) {
      /* Find the smaller child */
      heap_idx_t min_idx = HEAP_LEFT(i);
      HEAP_KEY_T min = heap->array[HEAP_LEFT(i)].key;
      if ( HEAP_RIGHT(i) < heap->size &&
         heap->array[HEAP_RIGHT(i)].key < min) {
        min = heap->array[HEAP_RIGHT(i)].key;
        min_idx = HEAP_RIGHT(i);
      }

      /* Check if minheap property is violated */
      if (min < key) {
        heap->array[i] = heap->array[min_idx];
        i = min_idx;
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
 * Remove the root of the heap
 */
#define HEAP_DEL_ROOT HEAP_NAME(del_root)
static inline void HEAP_DEL_ROOT(HEAP_T *heap)
{
  /* Shrink by one */
  heap->size--;

  if (heap->size > 0) {
    /* put last element up the top */
    heap->array[0] = heap->array[heap->size];
    HEAP_SIFT_DOWN(heap, 0);
  }
}

/*
 * Remove any entry of the heap
 */
#define HEAP_DEL_ENTRY HEAP_NAME(del_entry)
static inline void HEAP_DEL_ENTRY(HEAP_T *heap, heap_idx_t i)
{
  /* Shrink by one */
  heap->size--;

  if (i != heap->size) {
    /* put last element up the top */
    heap->array[i] = heap->array[heap->size];
    HEAP_SIFT_DOWN(heap, i);
  }
}

#define HEAP_POP_VAL HEAP_NAME(pop_val)
unused
static bool HEAP_POP_VAL(HEAP_T *heap, HEAP_VAL_T *result)
{
  if (HEAP_SIZE(heap) == 0) {
    result = NULL;
    return false;
  }

  *result = HEAP_ROOT_VAL(heap);
  HEAP_DEL_ROOT(heap);

  return true;
}

/* Increase the key of an entry in the heap.  This may cause it to
 * need to be moved down the heap.  Keep on swapping it down until
 * it is resolved */
#define HEAP_INCREASE_KEY HEAP_NAME(increase_key)
static inline void HEAP_INCREASE_KEY(HEAP_T *heap, heap_idx_t i, HEAP_KEY_T newkey) {
  assert(heap->size > i);
  HEAP_ENTRY_T *entry = &(heap->array[i]);

  HEAP_ASSERT(newkey >= entry->key);
  entry->key = newkey;
  HEAP_SIFT_DOWN(heap, i);
}

/*
 * Given an almost-heap, where everything has the
 * heap property, except A[i] might have key < its parent.
 * Sift up A[i] until it is a heap again
 */
#define HEAP_SIFT_UP HEAP_NAME(sift_up)
static inline void HEAP_SIFT_UP(HEAP_T *heap, heap_idx_t i) {
  assert(heap->size > i);
  HEAP_ENTRY_T entry, parent;
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

/*
  returns: true on success, false on memory allocation error
 */
#define HEAP_EXPAND HEAP_NAME(expand)
static inline bool
HEAP_EXPAND(HEAP_T *heap, uint32_t needed_size)
{
  /* Expand the array if needed */
  assert(heap->size <= heap->malloced_size);
  if (heap->malloced_size < needed_size) {
    /* need to expand - expand  1.5x (rounded up) */
    unsigned int new_size = (heap->malloced_size * 3) / 2 + 1;
    if (new_size < needed_size) {
      new_size = needed_size;
    }
    if (new_size < HEAP_MIN_SIZE)
    {
      new_size = HEAP_MIN_SIZE;
    }

    heap->array = realloc(heap->array, sizeof(HEAP_ENTRY_T) * new_size);
    if (heap->array == NULL) {
      return false;
    }

    heap->malloced_size = new_size;
  }
  return true;
}

/*
  Add new entry to heap
  returns: true on success, false on memory allocation error
 */
#define HEAP_ADD_ENTRY HEAP_NAME(add_entry)
static inline bool
HEAP_ADD_ENTRY(HEAP_T *heap, HEAP_ENTRY_T entry) {
  /* Make sure big enough */
  bool ok = HEAP_EXPAND(heap, heap->size + 1);
  if (!ok)
    return false;

  /* Add to end of the array */
  heap->array[heap->size] = entry;
  heap->size++;

  /* Rearrange so it a heap again */
  HEAP_SIFT_UP(heap, heap->size -1);
  return true;
}

#define HEAP_ADD HEAP_NAME(add)
static inline bool
HEAP_ADD(HEAP_T *heap, HEAP_KEY_T k, HEAP_VAL_T v)
{
  HEAP_ENTRY_T entry = {k, v};
  return HEAP_ADD_ENTRY(heap, entry);
}

/* Decrease the key of an entry in the heap.  This may cause it to
 * need to be moved up the heap. */
#define HEAP_DECREASE_KEY HEAP_NAME(decrease_key)
static inline void
HEAP_DECREASE_KEY(HEAP_T *heap, heap_idx_t i, HEAP_KEY_T newkey)
{
  HEAP_ENTRY_T *entry = &(heap->array[i]);
  HEAP_ASSERT(newkey <= entry->key);
  entry->key = newkey;
  HEAP_SIFT_UP(heap, i);
}

#ifndef HEAP_KEEP_DEFNS
// Cleanup defined macros
#undef HEAP_T
#undef HEAP_ENTRY_T
#undef HEAP_NAME
#undef HEAP_INIT
#undef HEAP_INIT_EMPTY
#undef HEAP_CLEAR_CALLBACK
#undef HEAP_CLEAR
#undef HEAP_CREATE
#undef HEAP_CHECK
#undef HEAP_SIZE
#undef HEAP_ROOT
#undef HEAP_ROOT_KEY
#undef HEAP_ROOT_VAL
#undef HEAP_SIFT_DOWN
#undef HEAP_DEL_ROOT
#undef HEAP_DEL_ENTRY
#undef HEAP_POP_VAL
#undef HEAP_INCREASE_KEY
#undef HEAP_SIFT_UP
#undef HEAP_DECREASE_KEY
#undef HEAP_EXPAND
#undef HEAP_ADD_ENTRY
#undef HEAP_ADD
#undef HEAP_DECREASE_KEY

#undef HEAP_NAME__
#undef HEAP_NAME_
#undef HEAP_NAME

#undef HEAP_KEY_T
#undef HEAP_VAL_T
#undef HEAP_PFX
#endif // HEAP_KEEP_DEFNS
