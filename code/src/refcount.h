
/*
  Helper functions to manipulate refcounts from a server
 */

#ifndef __XLB_REFCOUNT_H
#define __XLB_REFCOUNT_H

#include "adlb-defs.h"
#include "data.h"
#include "data_internal.h"

/** Represent change in refcount that must be applied */
typedef struct {
  adlb_datum_id id;
  adlb_refcounts rc;
  
  /** If true, we don't have ownership of reference:
      must acquire before doing anything to avoid
      race condition on freeing */
  bool must_acquire_initial;
} xlb_rc_change;

/** List of refcount changes */
typedef struct {
  xlb_rc_change *arr;
  int count;
  int size;
} xlb_rc_changes;

#define XLB_RC_CHANGES_INIT_SIZE 16

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


// Inline functions
static inline adlb_data_code xlb_rc_changes_init(xlb_rc_changes *c)
{
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
  return ADLB_DATA_SUCCESS;
}

static inline adlb_data_code xlb_rc_changes_expand(xlb_rc_changes *c,
                                              int to_add)
{
  if (c->arr != NULL &&
      c->count + to_add <= c->size)
  {
    // Big enough
    return ADLB_DATA_SUCCESS;
  } else {
    int new_size;
    if (c->arr == NULL)
    {
      new_size = XLB_RC_CHANGES_INIT_SIZE;
    }
    else
    {
      new_size = c->size * 2;
    }
    void *new_arr = realloc(c->arr, (size_t)new_size * sizeof(c->arr[0]));
    
    check_verbose(new_arr != NULL, ADLB_DATA_ERROR_OOM,
                  "Could not alloc array");
    c->arr = new_arr;
    c->size = new_size;
    return ADLB_DATA_SUCCESS;
  }
}

static inline void xlb_rc_changes_free(xlb_rc_changes *c)
{
  if (c->arr != NULL) {
    free(c->arr);
  }
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
}

#endif // __XLB_REFCOUNT_H
