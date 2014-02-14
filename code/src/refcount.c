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

static adlb_data_code
xlb_update_rc_id(adlb_datum_id id, int *read_rc, int *write_rc,
       bool release_read, bool release_write, adlb_refcounts to_acquire,
       xlb_rc_changes *changes);

adlb_data_code xlb_incr_rc_svr(adlb_datum_id id, adlb_refcounts change)
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
  adlb_notif_t notify = ADLB_NO_NOTIFS;
  adlb_data_code dc = xlb_data_reference_count(id, change,
          NO_SCAVENGE, NULL, NULL, &notify);
  ADLB_DATA_CHECK(dc);
  
  // handle notifications here if needed
  adlb_code rc = xlb_notify_all(&notify, id);
  check_verbose(rc == ADLB_SUCCESS, ADLB_DATA_ERROR_UNKNOWN,
      "Error processing notifications for <%"PRId64">", id);
  
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_incr_referand(adlb_datum_storage *d, adlb_data_type type,
                  bool release_read, bool release_write,
                  adlb_refcounts to_acquire,
                  xlb_rc_changes *changes)
{
  assert(d != NULL);
  refcount_scavenge to_acquire2 = { .subscript = ADLB_NO_SUB,
                                    .refcounts = to_acquire };
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
      dc = xlb_members_cleanup(&d->CONTAINER, false, release_read,
                              release_write, to_acquire2, changes);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_MULTISET:
      dc = xlb_multiset_cleanup(d->MULTISET, false, false, 
            release_read, release_write, to_acquire2, changes);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_STRUCT:
      // increment referand for all members in struct
      dc = xlb_struct_cleanup(d->STRUCT, false, 
          release_read, release_write, to_acquire,
          -1, changes);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_REF:
      // decrement reference
      dc = xlb_update_rc_id(d->REF.id, &d->REF.read_refs,
         &d->REF.write_refs, release_read, release_write,
         to_acquire, changes); 
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_FILE_REF:
      // TODO: need to track refcount here
      assert(false);
      /*
      // decrement references held
      dc = xlb_incr_rc_svr(d->FILE_REF.status_id, change);
      DATA_CHECK(dc);
      dc = xlb_incr_rc_svr(d->FILE_REF.filename_id, change);
      DATA_CHECK(dc);*/
      break;
    default:
      check_verbose(false, ADLB_DATA_ERROR_TYPE,
                    "datum_gc(): unknown type %u>", type);
      break;
  }
  return ADLB_DATA_SUCCESS;
}


/**
  Work out refcount change
  releasing: if we're releasing the local count
  curr_rc: int holding current refcount, will be updated
  acquire_rc: number to acquire
  acquired: if acquired at least one ref
  remainder: remaining change to apply, can be +ive or -ive
 */
static adlb_data_code
apply_rc_update(bool releasing, int *curr_rc, int acquire_rc,
                int *acquired, int *remainder)
{
  assert(*curr_rc > 0);
  assert(acquire_rc >= 0);

  if (releasing)
  {
    // if releasing refcount, must go to zero
    *remainder = acquire_rc - *curr_rc;
    *acquired = *remainder <= 0 ? acquire_rc
                                : acquire_rc - *remainder;
    *curr_rc = 0;
  }
  else 
  {
    // if not releasing refcount, must end up >= 1
    int max_acquire = *curr_rc - 1;

    *acquired = acquire_rc <= max_acquire ?
                acquire_rc : max_acquire;
    *curr_rc -= *acquired;
    *remainder = acquire_rc - *acquired;
  }

  return ADLB_DATA_SUCCESS;
}

// TODO: need to change this to support multiple IDs
static adlb_data_code
xlb_update_rc_id(adlb_datum_id id, int *read_rc, int *write_rc,
       bool release_read, bool release_write, adlb_refcounts to_acquire,
       xlb_rc_changes *changes)
{
  adlb_data_code dc;

  // Number we acquired 
  int read_acquired, write_acquired;
  // Remainder change that needs to be applied
  int read_remainder, write_remainder;

  dc = apply_rc_update(release_read, read_rc,
          to_acquire.read_refcount, &read_acquired, &read_remainder);
  check_verbose(dc == ADLB_DATA_SUCCESS, dc, "Error updating read "
            "refcount of <%"PRId64"> r=%i acquiring %i release:%i",
            id, *read_rc, to_acquire.read_refcount, (int)read_remainder);

  dc = apply_rc_update(release_write, write_rc,
          to_acquire.write_refcount, &write_acquired, &write_remainder);
  check_verbose(dc == ADLB_DATA_SUCCESS, dc, "Error updating write "
            "refcount of <%"PRId64"> r=%i acquiring %i release:%i",
            id, *write_rc, to_acquire.write_refcount, (int)write_remainder);

  if (read_remainder != 0 || write_remainder != 0)
  {
    // Need to apply further changes
    dc = xlb_rc_changes_expand(changes, 1);
    DATA_CHECK(dc);

    xlb_rc_change *change = &changes->arr[changes->count++];
    change->rc.read_refcount = read_remainder;
    change->rc.write_refcount = write_remainder;
    // If we don't own a ref, must acquire one before doing anything
    // that would cause referand to be freed
    change->must_preacquire = (read_remainder > 0 && read_acquired == 0) ||
                              (write_remainder > 0 && write_acquired == 0);
  }
  return ADLB_DATA_SUCCESS;
}

// Modify reference count of referands and maybe scavenge
adlb_data_code
xlb_update_rc_referand(adlb_datum_storage *d, adlb_data_type type,
       bool release_read, bool release_write, adlb_refcounts to_acquire,
       xlb_rc_changes *changes)
{
  assert(to_acquire.read_refcount >= 0);
  assert(to_acquire.write_refcount >= 0);
  assert(changes != NULL);

  // TODO: handle nested structures?
  // NOTE: should avoid traversing containers if no refs in value

  // TODO: travers aend use xlb_update_rc_id
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_incr_rc_scav(adlb_datum_id id, adlb_subscript subscript,
        const void *ref_data, int ref_data_len, adlb_data_type ref_type,
        adlb_refcounts decr_self, adlb_refcounts incr_referand,
        adlb_notif_t *notifications)
{
  assert(ADLB_RC_NONNEGATIVE(incr_referand));
  adlb_data_code dc;

  // Only do things if read refcounting is enabled
  if (!xlb_read_refcount_enabled && decr_self.write_refcount == 0 &&
                                    incr_referand.write_refcount == 0)
  {
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

