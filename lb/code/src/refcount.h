
/*
  Helper functions to manipulate refcounts from a server
 */

#ifndef __XLB_REFCOUNT_H
#define __XLB_REFCOUNT_H

#include "adlb-defs.h"
#include "data.h"
#include "data_internal.h"
#include "notifications.h"

/* Decrement reference count of given id.  Must be called on a server
   wait: if true, wait until refcount is confirmed. */
adlb_data_code
xlb_incr_refc_svr(adlb_datum_id id, adlb_refc change,
                adlb_notif_t *notifs, bool wait);

/* Modify reference count of locally stored datum.
   Send any consequent notifications or messages.
   suppress_errors: if true, just log any errors */
adlb_data_code
xlb_incr_refc_local(adlb_datum_id id, adlb_refc change,
                  bool suppress_errors);

/* Modify refcount of referenced items */
adlb_data_code
xlb_incr_referand(adlb_datum_storage *d, adlb_data_type type,
                  bool release_read, bool release_write,
                  xlb_refc_acquire to_acquire, xlb_refc_changes *changes);

#endif // __XLB_REFCOUNT_H
