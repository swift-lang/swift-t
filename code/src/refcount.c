#include "refcount.h"

#include <assert.h>
#include <stdio.h>

#include "adlb.h"
#include "common.h"
#include "data_cleanup.h"
#include "data_internal.h"
#include "data_structs.h"
#include "multiset.h"
#include "sync.h"

// TODO: maintain buffer of deferred refcount operations, 
//       pass up stack to be handled in bulk

/* Decrement reference count of given id from the server */
static adlb_data_code xlb_incr_rc_svr(adlb_datum_id id, adlb_refcounts change);

static adlb_data_code xlb_incr_rc_svr(adlb_datum_id id, adlb_refcounts change)
{
  assert(xlb_am_server); // Only makes sense to run on server

  if (!xlb_read_refcount_enabled)
    change.read_refcount = 0;

  DEBUG("server->server refcount <%"PRId64"> r += %i w += %i", id,
        change.read_refcount, change.write_refcount);
  
  if (ADLB_RC_IS_NULL(change))
    return ADLB_DATA_SUCCESS;

  int server = ADLB_Locate(id);
  if (server == xlb_comm_rank)
  {
    adlb_data_code dc = xlb_incr_rc_local(id, change, false);
    DATA_CHECK(dc);
  }
  else
  {
    struct packed_sync sync_msg;
    sync_msg.mode = ADLB_SYNC_REFCOUNT;
    sync_msg.incr.id = id;
    sync_msg.incr.change = change;
    adlb_code code = xlb_sync2(server, &sync_msg);
    check_verbose(code == ADLB_SUCCESS, ADLB_DATA_ERROR_UNKNOWN,
                  "Error syncing for refcount");
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_incr_rc_local(adlb_datum_id id, adlb_refcounts change,
                                 bool suppress_errors)
{
  adlb_notif_ranks notify = ADLB_NO_NOTIF_RANKS;
  adlb_data_code dc = xlb_data_reference_count(id, change,
          NO_SCAVENGE, NULL, NULL, &notify);
  ADLB_DATA_CHECK(dc);
  // handle notifications here if needed for some reason
  if (!xlb_notif_ranks_empty(&notify))
  {
    adlb_code rc = xlb_close_notify(id, &notify);
    check_verbose(rc == ADLB_SUCCESS, ADLB_DATA_ERROR_UNKNOWN,
        "Error processing notifications for <%"PRId64">", id);
  }
  return ADLB_DATA_SUCCESS;
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
      // TODO: borrow refcount here?
      dc = xlb_incr_rc_svr(d->REF.id, change);
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

// Modify reference count of referands and maybe scavenge
adlb_data_code
xlb_update_rc_referand(adlb_datum_storage *d, adlb_data_type type,
       bool release_read, bool release_write, adlb_refcounts to_acquire)
{
  assert(to_acquire.read_refcount >= 0);
  assert(to_acquire.write_refcount >= 0);

  // TODO: handle nested structures?

  // TODO: build list of refcounts to acquire

  // TODO: borrow refcounts if possible
  // - if releasing refcount, must go to zero
  // - if not releasing refcount, must end up >= 1

  // TODO: if can't borrow all, acquire

  // TODO: if can't give back one refcount, must increment
  blah
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_incr_rc_scav(adlb_datum_id id, adlb_subscript subscript,
        const void *ref_data, int ref_data_len, adlb_data_type ref_type,
        adlb_refcounts decr_self, adlb_refcounts incr_referand,
        adlb_notif_ranks *notifications)
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
      return xlb_data_referand_refcount(ref_data, ref_data_len, ref_type,
                                  id, incr_referand);
    }
  }
  else
  {
    // First attempt aborted and did nothing, do things one
    // step at a time
    DEBUG("Need to manually update refcounts for <%"PRId64">", id);
    dc = xlb_data_referand_refcount(ref_data, ref_data_len, ref_type,
                                    id, incr_referand);
    DATA_CHECK(dc);
    
    // Weren't able to acquire any reference counts, so attempted
    // decrement unsuccessful.  Retry.
    return xlb_rc_impl(d, id, adlb_rc_negate(decr_self), NO_SCAVENGE,
                          NULL, NULL, notifications);
  }
}

void xlb_rc_changes_free(xlb_rc_changes *c)
{
  if (c->arr != NULL) {
    free(c->arr);
  }
  c->arr = NULL;
  c->count = 0;
  c->size = 0;
}

