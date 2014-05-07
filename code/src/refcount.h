
/*
  Helper functions to manipulate refcounts from a server
 */

#ifndef __XLB_REFCOUNT_H
#define __XLB_REFCOUNT_H

#include "adlb-defs.h"
#include "data.h"
#include "data_internal.h"
#include "notifications.h"

/* Decrement reference count of given id.  Must be called on a server */
adlb_data_code
xlb_incr_rc_svr(adlb_datum_id id, adlb_refcounts change,
                adlb_notif_t *notifs);

/* Modify reference count of locally stored datum.
   Send any consequent notifications or messages.
   suppress_errors: if true, just log any errors */
adlb_data_code
xlb_incr_rc_local(adlb_datum_id id, adlb_refcounts change,
                  bool suppress_errors);

/* Modify refcount of referenced items */
adlb_data_code
xlb_incr_referand(adlb_datum_storage *d, adlb_data_type type,
                  bool release_read, bool release_write,
                  xlb_acquire_rc to_acquire, xlb_rc_changes *changes);

#endif // __XLB_REFCOUNT_H
