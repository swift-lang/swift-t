/*
  Data internal functions for cleaning up datums, including
  freeing memory and managing reference counts.
 */

#ifndef __XLB_DATA_CLEANUP_H
#define __XLB_DATA_CLEANUP_H

#include "adlb-defs.h"
#include "data.h"
#include "data_internal.h"


/* Cleanup datum by freeing memory and decrementing referand reference counts */
adlb_data_code cleanup_storage(adlb_datum_storage *d, adlb_data_type type,
         adlb_datum_id id, refcount_scavenge scav);

/* cleanup_storage with extra options that control how many reference
   counts of referands are decremented */
adlb_data_code
cleanup_storage2(adlb_datum_storage *d, adlb_data_type type,
             adlb_datum_id id, bool free_mem,
             adlb_refcounts rc_change, refcount_scavenge scav);


// Allow flexible freeing of memory/reference counts
adlb_data_code cleanup_members(adlb_container *container, bool free_mem,
                  adlb_refcounts change, refcount_scavenge scav);

#endif // __XLB_DATA_CLEANUP_H

