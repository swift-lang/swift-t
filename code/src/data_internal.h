/*
  Internal definitions for data and related modules
 */

#ifndef __XLB_DATA_INTERNAL_H
#define __XLB_DATA_INTERNAL_H

#include "adlb-defs.h"
#include "adlb_types.h"
#include "data.h"


/**
   Status vector for Turbine variables
 */
typedef struct {
  /** SET: Whether the value has been filled in */
  bool set : 1;
  /** PERMANENT: Whether garbage collection is disabled for data item */
  bool permanent : 1;
} adlb_data_status;


static inline void data_init_status(adlb_data_status *s)
{
  memset(s, 0, sizeof(*s));
}
#define ADLB_DATA_INIT_STATUS \
  { .set = 0, .permanent = 0 }

typedef struct
{
  adlb_data_type type;
  adlb_data_status status;
  int read_refcount; // Number of open read refs
  int write_refcount; // Number of open write refs
  adlb_datum_storage data;
  struct list_i listeners;
} adlb_datum;

#define verbose_error(code, format, args...)                \
  {                                                         \
    printf("ADLB DATA ERROR:\n");                           \
    printf(format "\n", ## args);                           \
    printf("\t in: %s()\n", __FUNCTION__);                  \
    printf("\t at: %s:%i\n", __FILE__, __LINE__);           \
    return code;                                            \
  }

#ifndef NDEBUG
/**
    Allows user to check an exceptional condition,
    print an error message, and return an error code in one swoop.
    This is disabled if NDEBUG is set
*/
#define check_verbose(condition, code, format, args...) \
  { if (! (condition))                                        \
    {                                                         \
      verbose_error(code, format, ## args)                    \
    }                                                         \
  }

#else
// Make this a noop if NDEBUG is set (for performance)
#define check_verbose(condition, code, format, args...) \
    (void) (condition);
#endif

adlb_data_code
datum_lookup(adlb_datum_id id, adlb_datum **d);

/*
  Alternative, more flexible implementation of refcount
  that directly takes datum reference
 */
adlb_data_code
refcount_impl(adlb_datum *d, adlb_datum_id id,
          adlb_refcounts change, refcount_scavenge scav,
          bool *garbage_collected, adlb_refcounts *refcounts_scavenged,
          adlb_ranks *notifications);

#endif // __XLB_DATA_INTERNAL_H
