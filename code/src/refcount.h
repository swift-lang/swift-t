
/*
  Helper functions to manipulate refcounts from a server
 */

#ifndef __XLB_REFCOUNT_H
#define __XLB_REFCOUNT_H

#include "adlb-defs.h"
#include "data.h"
#include "data_internal.h"

/* Modify reference count of locally stored datum.
   Send any consequent notifications or messages.
   suppress_errors: if true, just log any errors */
adlb_data_code
xlb_incr_rc_local(adlb_datum_id id, adlb_refcounts change,
                  bool suppress_errors);

/* Modify refcount of referenced items */
adlb_data_code
xlb_incr_referand(adlb_datum_storage *d,
        adlb_data_type type, adlb_refcounts change);

/* Modify refcount of referenced items.  If to_scavenge is positive,
   scavenge that number of read references to referands.
   release_read, release_write: if true, update read/write refcount here
              to zero, scavenging refcount and/or incrementing/decrementing
              referand
 */
adlb_data_code
xlb_update_rc_referand(adlb_datum_storage *d, adlb_data_type type,
       bool release_read, bool release_write, adlb_refcounts to_acquire);

/*
  Decrements refcount of datum, while incrementing
  refcount of other datums referred to by this datum
  TODO: in future, ability to offload this work to client
 */
adlb_data_code
xlb_incr_rc_scav(adlb_datum_id id, adlb_subscript subscript,
        const void *ref_data, int ref_data_len, adlb_data_type ref_type,
        adlb_refcounts decr_self, adlb_refcounts incr_referand,
        adlb_notif_ranks *notifications);

#endif // __XLB_REFCOUNT_H
