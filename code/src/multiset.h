#ifndef __XLB_MULTISET_H
#define __XLB_MULTISET_H

#include <stdlib.h>

#include "adlb-defs.h"
#include "data_internal.h"

/*
   Implement multiset data structure
 */

// Length of allocation chunk for multiset
#define XLB_MULTISET_CHUNK_SIZE 1024

typedef struct xlb_multiset_chunk_s xlb_multiset_chunk;

typedef struct adlb_multiset_s {
  adlb_data_type_short elem_type;
  xlb_multiset_chunk **chunks;
  uint chunk_count;      // Number of actual chunks
  uint chunk_arr_size;   // Allocated size of pointer array
  uint last_chunk_elems; // Number of actual data items
} xlb_multiset;

struct xlb_multiset_chunk_s {
  adlb_datum_storage arr[XLB_MULTISET_CHUNK_SIZE];
};


xlb_multiset *xlb_multiset_alloc(adlb_data_type elem_type);

void xlb_multiset_init(xlb_multiset *set, adlb_data_type elem_type);

uint xlb_multiset_size(const xlb_multiset *set);

/*
  Add datum to multiset.
  stored: if not NULL, this is set to a pointer to the data inside
          the multiset
 */
adlb_data_code
xlb_multiset_add(xlb_multiset *set, const void *data, int length,
                 adlb_refc refcounts, const adlb_datum_storage **stored);
/*
  Free memory and clear references from multiset
 */
adlb_data_code
xlb_multiset_cleanup(xlb_multiset *set, bool free_root, bool free_mem,
             bool release_read, bool release_write,
             xlb_refc_acquire to_acquire, xlb_refc_changes *refcs);

adlb_data_code
xlb_multiset_slice(xlb_multiset *set, uint start, uint count,
                                        adlb_slice_t **res);

void free_xlb_multiset_slice(adlb_slice_t *slice);

adlb_data_code
xlb_multiset_extract_slice(xlb_multiset *set, int start, int count,
              const adlb_buffer *caller_buffer, adlb_buffer *output); 

/*
 String representation of multiset.  Caller must free result.
 */
adlb_data_code
xlb_multiset_repr(xlb_multiset *set, char **repr);

#endif // __XLB_MULTISET_H
