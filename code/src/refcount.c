#include "refcount.h"

#include <assert.h>
#include <stdio.h>

#include "adlb.h"
#include "common.h"
#include "data_cleanup.h"
#include "data_structs.h"
#include "multiset.h"
#include "sync.h"

adlb_data_code xlb_incr_rc_svr(adlb_datum_id id, adlb_refcounts change)
{
  assert(xlb_am_server); // Only makes sense to run on server

  if (!xlb_read_refcount_enabled)
    change.read_refcount = 0;

  DEBUG("server->server refcount <%"PRId64"> r += %i w += %i", id,
        change.read_refcount, change.write_refcount);
  
  if (ADLB_RC_IS_NULL(change))
    return ADLB_DATA_SUCCESS;

  int rc = ADLB_DATA_SUCCESS;
  int server = ADLB_Locate(id);
  if (server == xlb_comm_rank)
  {
    adlb_ranks notify = ADLB_NO_RANKS;
    adlb_data_code dc = data_reference_count(id, change,
            NO_SCAVENGE, NULL, NULL, &notify);
    // TODO: handle notifications here if needed for some reason
    check_verbose(notify.count == 0, ADLB_DATA_ERROR_UNKNOWN,
        "Internal error: don't server->server write refcount decrements");
    return dc;
  }
  else
  {
    rc = xlb_sync(server);
    if (rc != ADLB_SUCCESS)
      return ADLB_DATA_ERROR_UNKNOWN;
    rc = ADLB_Refcount_incr(id, change);
    if (rc != ADLB_SUCCESS)
      return ADLB_DATA_ERROR_UNKNOWN;
    return ADLB_DATA_SUCCESS;
  }
}

adlb_data_code
xlb_incr_referand(adlb_datum_storage *d, adlb_data_type type,
                 adlb_refcounts change)
{
  assert(d != NULL);
  adlb_data_code dc;
  switch (type)
  {
    case ADLB_DATA_TYPE_STRING:
    case ADLB_DATA_TYPE_BLOB:
    case ADLB_DATA_TYPE_INTEGER:
    case ADLB_DATA_TYPE_FLOAT:
      // Types that don't hold references
      break;
    case ADLB_DATA_TYPE_CONTAINER:
      dc = xlb_members_cleanup(&d->CONTAINER, false, change, NO_SCAVENGE);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_MULTISET:
      dc = xlb_multiset_cleanup(d->MULTISET, false, false, change,
                                NO_SCAVENGE);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_STRUCT:
      // increment referand for all members in struct
      dc = xlb_struct_incr_referand(d->STRUCT, change);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_REF:
      // decrement reference
      dc = xlb_incr_rc_svr(d->REF, change);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_FILE_REF:
      // decrement references held
      dc = xlb_incr_rc_svr(d->FILE_REF.status_id, change);
      DATA_CHECK(dc);
      dc = xlb_incr_rc_svr(d->FILE_REF.filename_id, change);
      DATA_CHECK(dc);
      break;
    default:
      check_verbose(false, ADLB_DATA_ERROR_TYPE,
                    "datum_gc(): unknown type %u>", type);
      break;
  }
  return ADLB_DATA_SUCCESS;
}


// Modify reference count of referands and check scavenging
adlb_data_code
xlb_incr_scav_referand(adlb_datum_storage *d, adlb_data_type type,
        adlb_refcounts change, adlb_refcounts to_scavenge)
{
  assert(to_scavenge.read_refcount >= 0);
  assert(to_scavenge.write_refcount >= 0);

  if (ADLB_RC_IS_NULL(to_scavenge))
  {
    return xlb_incr_referand(d, type, change);
  }
  else
  {
    // Should cancel
    assert(to_scavenge.read_refcount == -change.read_refcount);
    assert(to_scavenge.write_refcount == -change.write_refcount);
    return ADLB_DATA_SUCCESS;
  }
}
adlb_data_code
xlb_incr_rc_scav(adlb_datum_id id, const char *subscript,
        const void *ref_data, int ref_data_len, adlb_data_type ref_type,
        adlb_refcounts decr_self, adlb_refcounts incr_referand,
        adlb_ranks *notifications)
{
  assert(ADLB_RC_NONNEGATIVE(incr_referand));
  adlb_data_code dc;

  // Only do things if read refcounting is enabled
  if (!xlb_read_refcount_enabled && decr_self.write_refcount == 0 &&
                                    incr_referand.write_refcount == 0)
  {
    // Make sure that notifications is initialized since refcount not
    // called on this branch
    notifications->count = 0;
    return ADLB_DATA_SUCCESS;
  }

  adlb_datum *d;
  dc = xlb_datum_lookup(id, &d);
  DATA_CHECK(dc);

  if (ADLB_RC_IS_NULL(incr_referand))
  {
    return xlb_rc_impl(d, id, adlb_rc_negate(decr_self),
                  NO_SCAVENGE, NULL, NULL, notifications);
  }

  // Must acquire referand refs here by scavenging from freed structure
  // or by incrementing
  adlb_refcounts scavenged;
  bool garbage_collected = false;
  refcount_scavenge scav_req = { .subscript = subscript,
                                 .refcounts = incr_referand };
  dc = xlb_rc_impl(d, id, adlb_rc_negate(decr_self), scav_req,
                        &garbage_collected, &scavenged, notifications);
  DATA_CHECK(dc);

  if (garbage_collected)
  {
    // Update based on what we scavenged  
    incr_referand.read_refcount -= scavenged.read_refcount;
    incr_referand.write_refcount -= scavenged.write_refcount;
    assert(ADLB_RC_NONNEGATIVE(incr_referand));
    if (ADLB_RC_IS_NULL(incr_referand))
    {
      DEBUG("Success in refcount switch for <%"PRId64">", id);
      return ADLB_DATA_SUCCESS;
    }
    else
    {
      DEBUG("Need to do extra increment of referands <%"PRId64">", id);
      return data_referand_refcount(ref_data, ref_data_len, ref_type,
                                  id, incr_referand);
    }
  }
  else
  {
    // First attempt aborted and did nothing, do things one
    // step at a time
    DEBUG("Need to manually update refcounts for <%"PRId64">", id);
    dc = data_referand_refcount(ref_data, ref_data_len, ref_type,
                                id, incr_referand);
    DATA_CHECK(dc);
    
    // Weren't able to acquire any reference counts, so attempted
    // decrement unsuccessful.  Retry.
    return xlb_rc_impl(d, id, adlb_rc_negate(decr_self), NO_SCAVENGE,
                          NULL, NULL, notifications);
  }
}

