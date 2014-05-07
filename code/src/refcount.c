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

adlb_data_code xlb_incr_rc_svr(adlb_datum_id id, adlb_refcounts change,
                               adlb_notif_t *notifs)
{
  assert(xlb_am_server); // Only makes sense to run on server

  adlb_data_code dc;

  if (!xlb_read_refcount_enabled)
    change.read_refcount = 0;

  DEBUG("server->server refcount <%"PRId64"> r += %i w += %i", id,
        change.read_refcount, change.write_refcount);
  
  if (ADLB_RC_IS_NULL(change))
    return ADLB_DATA_SUCCESS;

  int server = ADLB_Locate(id);
  if (server == xlb_comm_rank)
  {
    dc = xlb_data_reference_count(id, change, XLB_NO_ACQUIRE, NULL,
                                  notifs);
    DATA_CHECK(dc);
  }
  else
  {
    /*
     * If sending server->server, just send sync and don't wait for
     * response or notification to come back - might as well have the
     * other server do the work as this one
     */
    struct packed_sync sync_msg;
    sync_msg.mode = ADLB_SYNC_REFCOUNT;
    sync_msg.incr.id = id;
    sync_msg.incr.change = change;
    adlb_code code = xlb_sync2(server, &sync_msg, NULL);
    check_verbose(code == ADLB_SUCCESS, ADLB_DATA_ERROR_UNKNOWN,
                  "Error syncing for refcount");
  }
  return ADLB_DATA_SUCCESS;
}

adlb_data_code xlb_incr_rc_local(adlb_datum_id id, adlb_refcounts change,
                                 bool suppress_errors)
{
  adlb_notif_t notifs = ADLB_NO_NOTIFS;
  adlb_data_code dc = xlb_data_reference_count(id, change,
                           XLB_NO_ACQUIRE, NULL, &notifs);
  ADLB_DATA_CHECK(dc);
  
  // handle notifications here if needed
  adlb_code rc = xlb_notify_all(&notifs);
  check_verbose(rc == ADLB_SUCCESS, ADLB_DATA_ERROR_UNKNOWN,
      "Error processing notifications for <%"PRId64">", id);
  
  return ADLB_DATA_SUCCESS;
}

adlb_data_code
xlb_incr_referand(adlb_datum_storage *d, adlb_data_type type,
                  bool release_read, bool release_write,
                  xlb_acquire_rc to_acquire,
                  xlb_rc_changes *changes)
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
      dc = xlb_members_cleanup(&d->CONTAINER, false, release_read,
                              release_write, to_acquire, changes);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_MULTISET:
      dc = xlb_multiset_cleanup(d->MULTISET, false, false, 
            release_read, release_write, to_acquire, changes);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_STRUCT:
      // increment referand for all members in struct
      dc = xlb_struct_cleanup(d->STRUCT, false, 
          release_read, release_write, to_acquire, changes);
      DATA_CHECK(dc);
      break;
    case ADLB_DATA_TYPE_REF:
      assert(!adlb_has_sub(to_acquire.subscript));
      // decrement reference
      TRACE("xlb_incr_referand: <%"PRId64">", d->REF.id);
      dc = xlb_update_rc_id(d->REF.id, &d->REF.read_refs,
         &d->REF.write_refs, release_read, release_write,
         to_acquire.refcounts, changes); 
      DATA_CHECK(dc);
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
  assert(*curr_rc >= 0);
  assert(acquire_rc >= 0);

  TRACE("RC UP curr: %i", *curr_rc);

  check_verbose(acquire_rc == 0 || *curr_rc > 0,
        ADLB_DATA_ERROR_REFCOUNT_NEGATIVE, "Trying to acquire refcount,"
        " but own no references");

  if (releasing)
  {
    // if releasing refcount, must go to zero
    *remainder = acquire_rc - *curr_rc;
    *acquired = *remainder <= 0 ? acquire_rc
                                : acquire_rc - *remainder;
    *curr_rc = 0;
    TRACE("RC UP releasing: curr: %i acq: %i rem: %i", *curr_rc, *acquired,
                                                 *remainder);
  }
  else if (acquire_rc == 0)
  {
    // Do nothing
    *acquired = 0;
    *remainder = 0;
    TRACE("RC UP do nothing: curr: %i acq: %i rem: %i",
           *curr_rc, *acquired, *remainder);
  }
  else
  {
    // if not releasing refcount, must end up >= 1
    int max_acquire = *curr_rc - 1;

    *acquired = acquire_rc <= max_acquire ?
                acquire_rc : max_acquire;
    *curr_rc -= *acquired;
    *remainder = acquire_rc - *acquired;
    TRACE("RC acquire: curr: %i acq: %i rem: %i",
           *curr_rc, *acquired, *remainder);
  }

  return ADLB_DATA_SUCCESS;
}

static adlb_data_code
xlb_update_rc_id(adlb_datum_id id, int *read_rc, int *write_rc,
       bool release_read, bool release_write, adlb_refcounts to_acquire,
       xlb_rc_changes *changes)
{
  adlb_data_code dc;
  adlb_code ac;

  DEBUG("xlb_update_rc_id r: %i w:%i release_r: %i release_w: %i\
         acquire_r: %i acquire_w: %i", *read_rc, *write_rc,
          (int)release_read, (int)release_write,
          to_acquire.read_refcount, to_acquire.write_refcount);

  // Number we acquired 
  int read_acquired, write_acquired;
  // Remainder change that needs to be applied
  int read_remainder, write_remainder;

  if (xlb_read_refcount_enabled)
  {
    dc = apply_rc_update(release_read, read_rc,
            to_acquire.read_refcount, &read_acquired, &read_remainder);
    check_verbose(dc == ADLB_DATA_SUCCESS, dc, "Error updating read "
            "refcount of <%"PRId64"> r=%i acquiring %i release:%i",
            id, *read_rc, to_acquire.read_refcount, (int)release_read);
  }
  else
  {
    read_acquired = read_remainder = 0;
  }

  dc = apply_rc_update(release_write, write_rc,
          to_acquire.write_refcount, &write_acquired, &write_remainder);
  check_verbose(dc == ADLB_DATA_SUCCESS, dc, "Error updating write "
            "refcount of <%"PRId64"> w=%i acquiring %i release:%i",
            id, *write_rc, to_acquire.write_refcount, (int)release_write);

  if (read_remainder != 0 || write_remainder != 0)
  {
    // Need to apply further changes
    bool must_preacquire = (read_remainder > 0 && read_acquired == 0) ||
                              (write_remainder > 0 && write_acquired == 0);
    ac = xlb_rc_changes_add(changes, id, read_remainder, write_remainder,
                            must_preacquire);
    DATA_CHECK_ADLB(ac, ADLB_DATA_ERROR_OOM);
  }
  return ADLB_DATA_SUCCESS;
}
