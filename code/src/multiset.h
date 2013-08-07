#ifndef __XLB_MULTISET_H
#define __XLB_MULTISET_H

#include <stdlib.h>

#include "adlb-defs.h"
#include "data_internal.h"

/*
   Implement multiset data structure
 */

// Length of allocation chunk for multiset
#define ADLB_MULTISET_CHUNK_SIZE 1024

typedef struct adlb_multiset_chunk_s adlb_multiset_chunk;

typedef struct adlb_multiset_s {
  adlb_data_type_short elem_type;
  adlb_multiset_chunk **chunks;
  uint chunk_count;      // Number of actual chunks
  uint chunk_arr_size;   // Allocated size of pointer array
  uint last_chunk_elems; // Number of actual data items
} adlb_multiset;


void multiset_init(adlb_multiset *set, adlb_data_type elem_type);
uint multiset_size(const adlb_multiset *set);
adlb_data_code multiset_add(adlb_multiset *set, void *data, int length);

adlb_data_code multiset_free(adlb_multiset *set);
adlb_data_code multiset_slice(adlb_multiset *set, uint start, uint count,
                              slice_t **res);

void free_multiset_slice(slice_t *slice);
#endif // __XLB_MULTISET_H
