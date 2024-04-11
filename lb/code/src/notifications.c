#include "notifications.h"

#include "client_internal.h"
#include "common.h"
#include "handlers.h"
#include "messaging.h"
#include "refcount.h"
#include "server.h"
#include "sync.h"
#include "engine.h"

#define MAX_NOTIF_PAYLOAD (32+ADLB_DATA_SUBSCRIPT_MAX)

static adlb_code
xlb_process_local_notif_ranks(adlb_notif_ranks *ranks);

static adlb_code
xlb_close_notify(adlb_notif_ranks *ranks);

static adlb_code
xlb_notify_server_self(adlb_notif_rank *notif);

static adlb_code
xlb_refc_changes_apply(adlb_notif_t *notifs, bool apply_all,
                               bool apply_local, bool apply_preacquire);

static adlb_code
xlb_refc_cleanup(xlb_refc_changes *c, bool maintain_index);

static adlb_code
xlb_set_refs(adlb_notif_t *notifs, bool local_only);

static adlb_code
xlb_set_ref(adlb_datum_id id, adlb_subscript subscript,
          const void *value, size_t length, adlb_data_type type,
          adlb_refc transferred_refs, int write_decr,
          adlb_notif_t *notifs);

static adlb_code
xlb_notify_server(int server, adlb_datum_id id, adlb_subscript subscript);

static adlb_code
xlb_prepare_for_send(adlb_notif_t *notifs,
    const adlb_buffer *caller_buf,
    struct packed_notif_counts *client_counts,
    xlb_prepared_notifs *prepared);

// Returns size of payload including null terminator
static int fill_notif_payload(char *payload, adlb_datum_id id,
                              adlb_subscript subscript)
{
  int len_str;
  if (!adlb_has_sub(subscript))
  {
    len_str = sprintf(payload, "close %"PRId64"", id);
  }
  else
  {
    len_str = sprintf(payload, "close %"PRId64" %.*s", id,
             (int)subscript.length, (const char*)subscript.key);
  }
  return len_str + 1;
}

static adlb_code notify_local(int target, const char *payload, int length,
                              int work_type)
{
  int answer_rank = -1;
  adlb_put_opts opts = ADLB_DEFAULT_PUT_OPTS;
  opts.priority = 1;
  adlb_code rc = xlb_put_targeted_local(work_type, xlb_s.layout.rank,
               answer_rank, target, opts, payload, length);
  ADLB_CHECK(rc);
  return ADLB_SUCCESS;
}

static adlb_code notify_nonlocal(int target, int server,
                        const char *payload, int length,
                        int work_type)
{
  int answer_rank = -1;
  adlb_put_opts opts;
  opts.priority = 1;
  adlb_code rc;
  if (xlb_s.layout.am_server)
  {
    rc = xlb_sync(server);
    ADLB_CHECK(rc);
  }
  rc = ADLB_Put(payload, length, target, answer_rank, work_type, opts);
  ADLB_CHECK(rc);
  return ADLB_SUCCESS;
}


void xlb_free_notif(adlb_notif_t *notifs)
{
  xlb_free_ranks(&notifs->notify);
  xlb_free_datums(&notifs->references);
  xlb_refc_changes_free(&notifs->refcs);
  for (int i = 0; i < notifs->to_free_length; i++)
  {
    free(notifs->to_free[i]);
  }
  if (notifs->to_free != NULL)
  {
    free(notifs->to_free);
  }
}

void xlb_free_ranks(adlb_notif_ranks *ranks)
{
  if (ranks->notifs != NULL)
  {
    free(ranks->notifs);
    ranks->notifs = NULL;
    ranks->count = ranks->size = 0;
  }
}

void xlb_free_datums(adlb_ref_data *datums)
{
  if (datums->data != NULL)
  {
    free(datums->data);
    datums->data = NULL;
    datums->count = datums->size = 0;
  }
}

/*
   Set references.

   After returning, all matching references should be cleared
   from notifications.  Additional notifications and refcount
   operations may be added to notifications strcuture

   local_only: only set references local to server
 */
static adlb_code
xlb_set_refs(adlb_notif_t *notifs, bool local_only)
{
  adlb_code rc;
  adlb_ref_data *refs = &notifs->references;
  for (int i = 0; i < refs->count; i++)
  {
    const adlb_ref_datum *ref = &refs->data[i];

    bool set = false;
    if (!local_only || ADLB_Locate(ref->id) == xlb_s.layout.rank)
    {
      TRACE("Notifying reference %"PRId64" (%s)\n", ref->id,
            ADLB_Data_type_tostring(ref->type));
      rc = xlb_set_ref(ref->id, ref->subscript, ref->value,
                ref->value_len, ref->type, ref->refcounts,
                ref->write_decr, notifs);
      ADLB_CHECK(rc);

      ref = NULL; // Pointer may be invalid due to realloc
      set = true;
    }

    if (local_only && set)
    {
      // swap with last
      refs->count--;
      if (i != refs->count)
      {
        // Swap last to here
        refs->data[i] = refs->data[refs->count];
        i--; // Process new entry next
      }
    }
  }

  if (!local_only)
  {
    // We processed all, can clear
    xlb_free_datums(&notifs->references);
  }
  return ADLB_SUCCESS;
}

static adlb_code
xlb_set_ref(adlb_datum_id id, adlb_subscript subscript,
            const void *value, size_t length, adlb_data_type type,
            adlb_refc transferred_refs, int write_decr,
            adlb_notif_t *notifs)
{
  DEBUG("xlb_set_ref: <%"PRId64">[%.*s]=%p[%zu] r: %i w: %i "
      "write_decr: %i", id, (int)subscript.length,
      (const char*)subscript.key, value, length,
      transferred_refs.read_refcount, transferred_refs.write_refcount,
      write_decr);

  adlb_code rc = ADLB_SUCCESS;
  int server = ADLB_Locate(id);

  adlb_refc decr = { .read_refcount = 0, .write_refcount = write_decr };
  if (server == xlb_s.layout.rank)
  {
    // Ok to cast away const, since we're forcing it to copy
    adlb_data_code dc = xlb_data_store(id, subscript, (void*)value, length,
                    true, NULL, type, decr, transferred_refs, notifs);
    ADLB_DATA_CHECK(dc);

    return ADLB_SUCCESS;
  }

  // Store value, maybe accumulating more notification/ref setting work
  rc = xlb_store(id, subscript, type, value, length, decr,
                 transferred_refs, notifs);
  ADLB_CHECK(rc);
  TRACE("SET_REFERENCE DONE");
  return ADLB_SUCCESS;

}

/*
 * Send all notifications.
 */
static adlb_code
xlb_close_notify(adlb_notif_ranks *ranks)
{
  adlb_code rc;
  char payload[MAX_NOTIF_PAYLOAD];
  int payload_len = 0;
  adlb_subscript last_subscript = ADLB_NO_SUB;

  for (int i = 0; i < ranks->count; i++)
  {
    adlb_notif_rank *notif = &ranks->notifs[i];

    int target = notif->rank;
    int server = xlb_map_to_server(&xlb_s.layout, target);
    if (xlb_s.layout.am_server && target == xlb_s.layout.rank)
    {
      rc = xlb_notify_server_self(notif);
      ADLB_CHECK(rc);
    }
    else if (server == target)
    {
      // Server subscribed by sync
      rc = xlb_notify_server(server, notif->id, notif->subscript);
      ADLB_CHECK(rc);
    }
    else
    {
      if (i == 0 || notif->subscript.key != last_subscript.key ||
                    notif->subscript.length != last_subscript.length)
      {
        // Skip refilling payload if possible
        payload_len = fill_notif_payload(payload, notif->id,
                                          notif->subscript);
      }
      if (server == xlb_s.layout.rank)
      {
        rc = notify_local(target, payload, payload_len, notif->work_type);
        ADLB_CHECK(rc);
      }
      else
      {
        rc = notify_nonlocal(target, server, payload, payload_len,
                             notif->work_type);
        ADLB_CHECK(rc);
      }
    }
  }

  // Should be all processed now
  xlb_free_ranks(ranks);
  return ADLB_SUCCESS;
}

static adlb_code
xlb_notify_server(int server, adlb_datum_id id, adlb_subscript subscript)
{
  MPI_Status status;
  MPI_Request request;

  if (xlb_s.layout.am_server)
  {
    // Must use sync for server->server
    adlb_code ac = xlb_sync_notify(server, id, subscript);
    ADLB_CHECK(ac);
    return ADLB_SUCCESS;
  }

  size_t subscript_len = adlb_has_sub(subscript) ? subscript.length : 0;
  assert(subscript_len <= ADLB_DATA_SUBSCRIPT_MAX);

  TRACE("notify_server(<%"PRId64">[%.*s]) => server %i",
         id, (int)subscript_len, (char*)subscript.key, server);

  // Stack allocate small buffer
  size_t hdr_len = sizeof(struct packed_notify_hdr) + subscript_len;
  char hdr_buffer[hdr_len];
  struct packed_notify_hdr *hdr = (struct packed_notify_hdr*)hdr_buffer;

  // Fill in header
  hdr->id = id;
  hdr->subscript_len = subscript_len;
  if (subscript_len > 0)
  {
    memcpy(hdr->subscript, subscript.key, subscript_len);
  }

  int response;
  IRECV(&response, 1, MPI_INT, server, ADLB_TAG_RESPONSE);
  SEND(hdr, (int)hdr_len, MPI_BYTE, server, ADLB_TAG_NOTIFY);
  WAIT(&request,&status);

  return (adlb_code)response;
}

/*
  Handle notification destined for this server
 */
static adlb_code
xlb_notify_server_self(adlb_notif_rank *notif)
{
  assert(xlb_s.layout.am_server && notif->rank == xlb_s.layout.rank);
  /*
    Handle notification for self, which must have been initiated
    by a Turbine engine subscribe.  This may result in work being
    released.  This will be stored in xlb_server_ready_work to be
    later processed by the server loop
   */
  xlb_engine_code ec;
  if (adlb_has_sub(notif->subscript))
  {
    ec = xlb_engine_sub_close(notif->id, notif->subscript,
                          false, &xlb_server_ready_work);
    ADLB_ENGINE_CHECK(ec);
  }
  else
  {
    ec = xlb_engine_close(notif->id, false, &xlb_server_ready_work);

    ADLB_ENGINE_CHECK(ec);
  }
  return ADLB_SUCCESS;
}

adlb_code
xlb_process_local_notif(adlb_notif_t *notifs)
{
  assert(xlb_s.layout.am_server);

  adlb_code ac;

  // Whether there may be refs or refcs left to process locally
  bool maybe_local_remaining = true;
  do
  {
    // First set references where necessary
    // This may result in adding some refcount changes
    ac = xlb_set_refs(notifs, true);
    ADLB_CHECK(ac);

    int refs_count = notifs->references.count;

    // Do local refcounts second, since they can add additional notifs
    // Also do pre-increments here, since we can't send them
    ac = xlb_refc_changes_apply(notifs, false, true, true);
    ADLB_CHECK(ac);

    // Check if any new refs appeared, they may be local
    if (notifs->references.count == refs_count)
    {
      // Didn't add any new refs
      maybe_local_remaining = false;
    }
  } while (maybe_local_remaining);

  // Finally send notification messages, which will not add any
  // additional work
  ac = xlb_process_local_notif_ranks(&notifs->notify);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

/*
  Process local notifications on this server
 */
static adlb_code
xlb_process_local_notif_ranks(adlb_notif_ranks *ranks)
{
  assert(xlb_s.layout.am_server);
  if (ranks->count > 0)
  {
    char payload[MAX_NOTIF_PAYLOAD];
    int payload_len = 0;
    adlb_subscript last_subscript = ADLB_NO_SUB;

    int i = 0;
    while (i < ranks->count)
    {
      adlb_notif_rank *notif = &ranks->notifs[i];
      int target = notif->rank;
      bool processed_locally = false;
      if (xlb_s.layout.am_server && target == xlb_s.layout.rank)
      {
        adlb_code rc = xlb_notify_server_self(notif);
        ADLB_CHECK(rc);
        processed_locally = true;
      } else {
        int server = xlb_map_to_server(&xlb_s.layout, target);
        if (server == xlb_s.layout.rank)
        {
          // Check to see if target is worker belonging to server
          if (i == 0 || notif->subscript.key != last_subscript.key ||
                        notif->subscript.length != last_subscript.length)
          {
            // Skip refilling payload if possible
            payload_len = fill_notif_payload(payload, notif->id,
                                               notif->subscript);
          }

          // Swap with last and shorten array
          adlb_code rc = notify_local(target, payload, payload_len,
                                      notif->work_type);
          ADLB_CHECK(rc);
          processed_locally = true;
        }
      }

      if (processed_locally)
      {
        ranks->notifs[i] = ranks->notifs[ranks->count - 1];
        ranks->count--;
      }
      else
      {
        i++;
      }
    }

    // Free memory if we managed to remove some
    if (ranks->count == 0 && ranks->notifs != NULL)
    {
      xlb_free_ranks(ranks);
    }
  }
  return ADLB_SUCCESS;
}

adlb_code
xlb_notify_all(adlb_notif_t *notifs)
{
  adlb_code rc;
  /*
   * Apply rc changes and refs before notifs because they may add new
   * notifications.  It is possible for refs to cause adding rc changes
   * and vice-versa, so need to loop
   */
  do
  {
    if (!xlb_refc_changes_empty(&notifs->refcs))
    {
      rc = xlb_refc_changes_apply(notifs, true, true, true);
      ADLB_CHECK(rc);
    }
    assert(xlb_refc_changes_empty(&notifs->refcs));

    if (!xlb_refs_empty(&notifs->references))
    {
      rc = xlb_set_refs(notifs, false);
      ADLB_CHECK(rc);
    }
    assert(xlb_refs_empty(&notifs->references));
  } while (!xlb_refc_changes_empty(&notifs->refcs));

  assert(xlb_refs_empty(&notifs->references));
  assert(xlb_refc_changes_empty(&notifs->refcs));

  // Sending notifications doesn't result in additional work
  if (!xlb_notif_ranks_empty(&notifs->notify))
  {
    rc = xlb_close_notify(&notifs->notify);
    ADLB_CHECK(rc);
  }
  assert(xlb_notif_ranks_empty(&notifs->notify));
  assert(xlb_refc_changes_empty(&notifs->refcs));
  assert(xlb_refs_empty(&notifs->references));

  // Check all were cleared
  assert(xlb_notif_empty(notifs));
  return ADLB_SUCCESS;
}

/*
 * Apply refcount changes and remove entries from list.
 * This will add additional notifications to the notifs structure if
 *  freeing/closing data results in more notifications.
 * It may also result in more refs being added to structure
 * All rc changes in notif matching criteria will be removed, including any
 * that were added during processing
 *
 * apply_all: if true, apply all changes
 * apply_local: if true, apply changes to local data
 * apply_preacquire: if true, apply changes that must be applied early
 */
static adlb_code
xlb_refc_changes_apply(adlb_notif_t *notifs, bool apply_all,
                               bool apply_local, bool apply_preacquire)
{
  DEBUG("xlb_refc_changes_apply(): applying local refcounts");
  adlb_data_code dc;
  xlb_refc_changes *c = &notifs->refcs;
  bool any_applied = false;
  for (int i = 0; i < c->count; i++)
  {
    DEBUG("xlb_refc_changes_apply(): applying local refcount %i", i);
    bool applied = false;
    xlb_refc_change *change = &c->arr[i];
    if (ADLB_REFC_IS_NULL(change->rc))
    {
      // Don't need to apply null refcounts
      applied = true;
    }
    else if (apply_all || (apply_preacquire && change->must_preacquire))
    {
      // Apply reference count operation
      if (xlb_s.layout.am_server)
      {
        // update reference count (can be local or remote)
        bool wait = change->must_preacquire;
        dc = xlb_incr_refc_svr(change->id, change->rc, notifs,
                               wait);
        ADLB_DATA_CHECK(dc);
      }
      else
      {
        // Increment refcount, maybe accumulating more notifications
        adlb_code ac = xlb_refcount_incr(change->id, change->rc, notifs);
        ADLB_CHECK(ac);
      }
      applied = true;
    }
    else if (apply_local && ADLB_Locate(change->id) == xlb_s.layout.rank)
    {
      // Process locally and added consequential notifications to list
      dc = xlb_data_reference_count(change->id, change->rc, XLB_NO_ACQUIRE,
                                    NULL, notifs);
      ADLB_DATA_CHECK(dc);
      applied = true;
    }

    if (applied)
    {
      any_applied = true;

      // Array may have been realloced, old pointer maybe invalid
      change = &c->arr[i];

      // Set to zero and remove remove later
      change->rc.read_refcount = change->rc.write_refcount = 0;
    }
  }

  if (apply_all)
  {
    // All changes applied - discard everything
    xlb_refc_changes_free(c);
  }
  else if (any_applied)
  {
    // Cleanup any empty entries, unless we applied all, in which case
    // we can throw everything away
    adlb_code ac = xlb_refc_cleanup(c, true);
    ADLB_CHECK(ac);

    // Free memory if none present
    if (c->count == 0)
    {
      xlb_refc_changes_free(c);
    }
  }


  return ADLB_SUCCESS;
}

/*
  Remove any refcount entries with 0 read/0 write
 */
static adlb_code
xlb_refc_cleanup(xlb_refc_changes *c, bool maintain_index)
{
  int insert_ix = 0;
  for (int i = 0; i < c->count; i++)
  {
    xlb_refc_change *change = &c->arr[i];
    if (change->rc.read_refcount == 0 &&
        change->rc.write_refcount == 0)
    {
#if XLB_INDEX_REFC_CHANGES
      // Will need to update or invalidate index if not processing all
      bool maintain_index = !apply_all;
      if (maintain_index)
      {
        void *tmp;
        bool removed = table_lp_remove(&c->index, change->id, &tmp);
        DEBUG("Remove change for id <%"PRId64">: %lu", change->id,
              (unsigned long)tmp);
        assert(removed);
        assert(((unsigned long)tmp) == i);
      }
#endif
    }
    else
    {
      if (i != insert_ix)
      {
        c->arr[insert_ix] = c->arr[i];
#if XLB_INDEX_REFC_CHANGES
        if (maintain_index)
        {
          void *ptr;
          bool found = table_lp_set(&c->index, change->id,
                      (void*)(unsigned long)(insert_ix), &ptr);
          assert(found);
          assert(((unsigned long)ptr) == i);
          TRACE("Reindex change for id <%"PRId64">: %lu => %d",
                change->id, (unsigned long)ptr, i);
        }
#endif
      }
      insert_ix++;
    }
  }

  c->count = insert_ix;

  return ADLB_SUCCESS;
}

adlb_code
xlb_notifs_expand(adlb_notif_ranks *notifs, int to_add)
{
  assert(to_add >= 0);
  int needed = notifs->count + to_add;
  if (notifs->size >= needed) {
    return ADLB_SUCCESS;
  }

  int new_size = notifs->size == 0 ?
                    XLB_NOTIFS_INIT_SIZE : notifs->size * 2;
  if (new_size < needed)
    new_size = needed;

  void *ptr = realloc(notifs->notifs, sizeof(notifs->notifs[0]) *
                                      (size_t)new_size);
  ADLB_CHECK_MALLOC(ptr);

  notifs->notifs = ptr;
  notifs->size = new_size;
  return ADLB_SUCCESS;
}

adlb_code
xlb_refs_expand(adlb_ref_data *refs, int to_add)
{
  assert(to_add >= 0);

  int needed = refs->count + to_add;
  if (refs->size >= needed) {
    return ADLB_SUCCESS;
  }

  int new_size = refs->size == 0 ?
                    XLB_REFS_INIT_SIZE : refs->size * 2;
  if (new_size < needed)
    new_size = needed;

  void *ptr = realloc(refs->data, sizeof(refs->data[0]) *
                       (size_t)new_size);
  ADLB_CHECK_MALLOC(ptr);

  refs->data = ptr;
  refs->size = new_size;
  return ADLB_SUCCESS;
}

adlb_code
xlb_refc_changes_expand(xlb_refc_changes *c, int to_add)
{
  assert(to_add >= 0);
  int needed = c->count + to_add;
  if (c->size >= needed)
  {
    return ADLB_SUCCESS;
  }

  int new_size = (c->size == 0) ? XLB_REFC_CHANGES_INIT_SIZE : c->size * 2;
  if (new_size < needed)
    new_size = needed;

  void *ptr = realloc(c->arr, (size_t)new_size * sizeof(c->arr[0]));
  ADLB_CHECK_MALLOC(ptr);

#if XLB_INDEX_REFC_CHANGES
  // Init index, use 1.0 load factor so realloced at same pace as array
  if (!table_lp_init_custom(&c->index, new_size, 1.0))
  {
    ERR_PRINTF("Could not alloc table");
    free(ptr);
    return ADLB_ERROR;
  }
#endif

  c->arr = ptr;
  c->size = new_size;
  return ADLB_SUCCESS;
}

adlb_code
xlb_to_free_expand(adlb_notif_t *notifs, int to_add)
{
  assert(to_add >= 0);
  int needed = notifs->to_free_length + to_add;
  if (notifs->to_free_size >= needed)
  {
    return ADLB_SUCCESS;
  }

  int new_size = notifs->to_free_size == 0 ?
            XLB_TO_FREE_INIT_SIZE : notifs->to_free_size * 2;
  if (new_size < needed)
    new_size = needed;

  void *ptr = realloc(notifs->to_free,
                    sizeof(notifs->to_free[0]) * (size_t)new_size);
  ADLB_CHECK_MALLOC(ptr);

  notifs->to_free = ptr;
  notifs->to_free_size = new_size;
  return ADLB_SUCCESS;
}

adlb_code
xlb_prepare_notif_work(adlb_notif_t *notifs,
        const adlb_buffer *caller_buffer,
        struct packed_notif_counts *client_counts,
        xlb_prepared_notifs *prepared, bool *must_send)
{
  adlb_code rc;
  if (ADLB_CLIENT_NOTIFIES)
  {
    // Remove any notifications that can be handled locally
    rc = xlb_process_local_notif(notifs);
    ADLB_CHECK(rc);

    if (xlb_notif_empty(notifs))
    {
      // Initialize counts to all zeroes
      memset(client_counts, 0, sizeof(*client_counts));
      *must_send = false;
      return ADLB_SUCCESS;
    }

    *must_send = true;
    rc = xlb_prepare_for_send(notifs, caller_buffer, client_counts,
                              prepared);
    ADLB_CHECK(rc);
  }
  else
  {
    // TODO: if disabling ADLB_CLIENT_NOTIFIES, would need
    // to ensure that this is never called while already
    // in a sync loop
    // Handle on server
    rc = xlb_notify_all(notifs);
    ADLB_CHECK(rc);

    // Initialize counts to all zeroes
    memset(client_counts, 0, sizeof(*client_counts));

    *must_send = false;
  }

  return ADLB_SUCCESS;
}

/**
 * Assemble data into buffers in preparation for sending to
 * another rank
 */
static adlb_code
xlb_prepare_for_send(adlb_notif_t *notifs,
    const adlb_buffer *caller_buf,
    struct packed_notif_counts *client_counts,
    xlb_prepared_notifs *prepared)
{
  int notify_count = notifs->notify.count;
  int refs_count = notifs->references.count;
  int refcs_count = notifs->refcs.count;
  assert(notify_count >= 0);
  assert(refs_count >= 0);
  assert(refcs_count >= 0);

  adlb_data_code dc;

  /*
    First allocate buffers for packed notifications and references:
    we know the side we need in advance
   */

  size_t caller_buf_size = (caller_buf == NULL) ?  0 : caller_buf->length;
  size_t caller_buf_used = 0;

  struct packed_notif *packed_notifs = NULL;
  struct packed_reference *packed_refs = NULL;
  prepared->free_packed_notifs = false;
  prepared->free_packed_refs = false;

  if (notify_count > 0)
  {
    // Grab enough space for packed notifs in buffer or by mallocing
    size_t notif_bytes = sizeof(struct packed_notif) * (size_t) notify_count;
    if (caller_buf_used + notif_bytes <= caller_buf_size)
    {
      packed_notifs = (struct packed_notif *)
                    &caller_buf->data[caller_buf_used];
      caller_buf_used += notif_bytes;
    }
    else
    {
      packed_notifs = malloc(notif_bytes);
      ADLB_CHECK_MALLOC(packed_notifs);
      prepared->free_packed_notifs = true;
    }
    prepared->packed_notifs = packed_notifs;
  }

  if (refs_count > 0)
  {
    // Grab enough space for packed refs in buffer or by mallocing
    size_t refs_bytes = sizeof(struct packed_reference) * (size_t) refs_count;
    if (caller_buf_used + refs_bytes <= caller_buf_size)
    {
      packed_refs = (struct packed_reference*)
                  &caller_buf->data[caller_buf_used];
      caller_buf_used += refs_bytes;
    }
    else
    {
      packed_refs = malloc(refs_bytes);
      ADLB_CHECK_MALLOC(packed_refs);
      prepared->free_packed_refs = true;
    }
    prepared->packed_refs = packed_refs;
  }

  // Remainder of bufer
  bool using_caller_buf2 = true;
  adlb_buffer caller_buf2;
  caller_buf2.data = caller_buf == NULL ?
             NULL : caller_buf->data + caller_buf_used;
  caller_buf2.length = caller_buf_size - caller_buf_used;

  /*
   We need to send subscripts and values back to client, so we pack them
   all into a buffer, and send them back.  We can then reference them by
   their index in the buffer.
   */
  adlb_buffer extra_data;
  dc = ADLB_Init_buf(&caller_buf2, &extra_data, &using_caller_buf2, 0);
  ADLB_DATA_CHECK(dc);

  size_t extra_pos = 0;
  int extra_data_count = 0;

  // Track last subscript so we don't send redundant subscripts
  const adlb_subscript *last_subscript = NULL;
  int last_subscript_ix = -1;

  for (int i = 0; i < notify_count; i++)
  {
    adlb_notif_rank *rank = &notifs->notify.notifs[i];
    packed_notifs[i].rank = rank->rank;
    packed_notifs[i].id = rank->id;
    if (adlb_has_sub(rank->subscript))
    {
      if (last_subscript != NULL &&
          last_subscript->key == rank->subscript.key &&
          last_subscript->length == rank->subscript.length)
      {
        // Same as last
        packed_notifs[i].subscript_data = last_subscript_ix;
      }
      else
      {
        packed_notifs[i].subscript_data = extra_data_count;

        // pack into extra data
        dc = ADLB_Append_buffer(ADLB_DATA_TYPE_NULL,rank->subscript.key,
            rank->subscript.length, true, &extra_data,
            &using_caller_buf2, &extra_pos);
        ADLB_DATA_CHECK(dc);

        last_subscript = &rank->subscript;
        last_subscript_ix = extra_data_count;
        extra_data_count++;
      }
    }
    else
    {
      packed_notifs[i].subscript_data = -1; // No subscript
    }
  }

  // Track last value so we don't send redundant values
  const void *last_value = NULL;
  size_t last_value_len;
  int last_value_ix;
  for (int i = 0; i < refs_count; i++)
  {
    adlb_ref_datum *ref = &notifs->references.data[i];
    packed_refs[i].id = ref->id;
    packed_refs[i].type = ref->type;
    packed_refs[i].refcounts = ref->refcounts;
    packed_refs[i].write_decr = ref->write_decr;

    if (adlb_has_sub(ref->subscript))
    {
      packed_refs[i].subscript_data = extra_data_count++;
      dc = ADLB_Append_buffer(ADLB_DATA_TYPE_NULL, ref->subscript.key,
          ref->subscript.length, true, &extra_data,
          &using_caller_buf2, &extra_pos);
      ADLB_DATA_CHECK(dc);
    }
    else
    {
      packed_refs[i].subscript_data = -1;
    }

    if (last_value != NULL &&
        last_value == ref->value &&
        last_value_len == ref->value_len)
    {
      // Same as last
      packed_refs[i].val_data = last_value_ix;
    }
    else
    {
      packed_refs[i].val_data = extra_data_count;
      dc = ADLB_Append_buffer(ADLB_DATA_TYPE_NULL, ref->value,
          ref->value_len, true, &extra_data, &using_caller_buf2,
          &extra_pos);
      ADLB_DATA_CHECK(dc);

      last_value = ref->value;
      last_value_len = ref->value_len;
      last_value_ix = extra_data_count;
      extra_data_count++;
    }
  }

  // Store remaining things to output structs
  prepared->extra_data = extra_data.data;
  prepared->free_extra_data = !using_caller_buf2;

  client_counts->notify_count = notify_count;
  client_counts->reference_count = refs_count;
  client_counts->refc_count = refcs_count;
  client_counts->extra_data_count = extra_data_count;
  client_counts->extra_data_bytes = extra_pos;

  return ADLB_SUCCESS;
}

adlb_code
xlb_send_notif_work(int caller, adlb_notif_t *notifs,
       const struct packed_notif_counts *counts,
       const xlb_prepared_notifs *prepared)
{
  size_t extra_data_bytes = counts->extra_data_bytes;
  int notify_count = counts->notify_count;
  int refs_count = counts->reference_count;
  int refcs_count = counts->refc_count;

  if (extra_data_bytes > 0)
  {
    TRACE("Sending %i extra data count %zu bytes",
           counts->extra_data_count, extra_data_bytes);
    assert(counts->extra_data_count > 0);
    mpi_send_big(prepared->extra_data, extra_data_bytes,
                 caller, ADLB_TAG_RESPONSE_NOTIF);

    if (prepared->free_extra_data)
    {
      free(prepared->extra_data);
    }
  }
  if (notify_count > 0)
  {
    struct packed_notif *packed_notifs = prepared->packed_notifs;
    TRACE("Sending %i notifs", notify_count);
    SEND(packed_notifs, notify_count * (int)sizeof(packed_notifs[0]),
         MPI_BYTE, caller, ADLB_TAG_RESPONSE_NOTIF);
    if (prepared->free_packed_notifs)
    {
      free(packed_notifs);
    }
  }
  if (refs_count > 0)
  {
    TRACE("Sending %i refs", refs_count);
    struct packed_reference *packed_refs = prepared->packed_refs;
    SEND(packed_refs, refs_count * (int)sizeof(packed_refs[0]), MPI_BYTE,
         caller, ADLB_TAG_RESPONSE_NOTIF);
    if (prepared->free_packed_refs)
    {
      free(packed_refs);
    }
  }
  if (refcs_count > 0)
  {
    TRACE("Sending %i rc changes", refcs_count);

    SEND(notifs->refcs.arr,
         refcs_count * (int)sizeof(notifs->refcs.arr[0]), MPI_BYTE,
         caller, ADLB_TAG_RESPONSE_NOTIF);
  }

  TRACE("Done sending notifs");

  return ADLB_SUCCESS;
}

/*
  Receive notification messages from server and process them
 */
adlb_code
xlb_handle_client_notif_work(const struct packed_notif_counts *counts,
                        int to_server_rank)
{
  adlb_code rc;

  // Take care of any notifications that the client must do
  adlb_notif_t notifs = ADLB_NO_NOTIFS;

  rc = xlb_recv_notif_work(counts, to_server_rank, &notifs);
  ADLB_CHECK(rc);

  rc = xlb_notify_all(&notifs);
  ADLB_CHECK(rc);

  xlb_free_notif(&notifs);

  return ADLB_SUCCESS;
}

adlb_code
xlb_recv_notif_work(const struct packed_notif_counts *counts,
    int to_server_rank, adlb_notif_t *notifs)
{
  adlb_code ac;
  adlb_data_code dc;
  MPI_Status status;

  void *extra_data = NULL;
  adlb_binary_data *extra_data_ptrs = NULL; // Pointers into buffer
  int extra_data_count = 0;
  if (counts->extra_data_bytes > 0)
  {
    size_t bytes = counts->extra_data_bytes;
    extra_data_count = counts->extra_data_count;
    assert(extra_data_count >= 0);

    extra_data = malloc(bytes);
    ADLB_CHECK_MALLOC(extra_data);

    ac = xlb_to_free_add(notifs, extra_data);
    ADLB_CHECK(ac);

    ac = mpi_recv_big(extra_data, bytes, to_server_rank, ADLB_TAG_RESPONSE_NOTIF);
    ADLB_CHECK(ac);

    // Locate the separate data entries in the buffer
    extra_data_ptrs = malloc(sizeof(extra_data_ptrs[0]) *
                             (size_t)extra_data_count);
    ADLB_CHECK_MALLOC(extra_data_ptrs);

    ac = xlb_to_free_add(notifs, extra_data_ptrs);
    ADLB_CHECK(ac)

    size_t pos = 0;
    for (int i = 0; i < extra_data_count; i++)
    {
      dc = ADLB_Unpack_buffer(ADLB_DATA_TYPE_NULL, extra_data, bytes, &pos,
              &extra_data_ptrs[i].data, &extra_data_ptrs[i].length);
      ADLB_DATA_CHECK(dc);
    }
    assert(pos == bytes); // Should consume all of buffer
  }

  if (counts->notify_count > 0)
  {
    TRACE("Receiving %i notifs", counts->notify_count);
    int added_count = counts->notify_count;
    ac = xlb_notifs_expand(&notifs->notify, added_count);
    ADLB_CHECK(ac);

    struct packed_notif *tmp = malloc(sizeof(struct packed_notif) *
                                             (size_t)added_count);
    ADLB_CHECK_MALLOC(tmp);

    RECV(tmp, (int)sizeof(tmp[0]) * added_count,
        MPI_BYTE, to_server_rank, ADLB_TAG_RESPONSE_NOTIF);

    for (int i = 0; i < added_count; i++)
    {
      // Copy data from tmp and fill in values
      adlb_notif_rank *r;
      r = &notifs->notify.notifs[notifs->notify.count + i];
      r->rank = tmp[i].rank;
      r->id = tmp[i].id;
      if (tmp[i].subscript_data == -1)
      {
        // No subscript
        r->subscript = ADLB_NO_SUB;
      }
      else
      {
        assert(tmp[i].subscript_data >= 0 &&
               tmp[i].subscript_data < extra_data_count);
        adlb_binary_data *data = &extra_data_ptrs[tmp[i].subscript_data];
        assert(data->data != NULL);
        r->subscript.key = data->data;
        r->subscript.length = data->length;
      }
    }
    notifs->notify.count += added_count;
    free(tmp);
  }

  if (counts->reference_count > 0)
  {
    TRACE("Receiving %i refs", counts->reference_count);
    int added_count = counts->reference_count;
    ac = xlb_refs_expand(&notifs->references, added_count);
    ADLB_CHECK(ac);

    struct packed_reference *tmp =
        malloc(sizeof(struct packed_reference) * (size_t)added_count);
    ADLB_CHECK_MALLOC(tmp);

    RECV(tmp, added_count * (int)sizeof(tmp[0]), MPI_BYTE,
         to_server_rank, ADLB_TAG_RESPONSE_NOTIF);

    for (int i = 0; i < added_count; i++)
    {
      // Copy data from tmp and fill in values
      adlb_ref_datum *d;
      d = &notifs->references.data[notifs->references.count + i];
      d->id = tmp[i].id;
      d->type = tmp[i].type;
      d->refcounts = tmp[i].refcounts;
      d->write_decr = tmp[i].write_decr;

      int sub_data_ix = tmp[i].subscript_data;
      if (sub_data_ix >= 0)
      {
        assert(sub_data_ix >= 0 && sub_data_ix < extra_data_count);
        adlb_binary_data *sub_data = &extra_data_ptrs[sub_data_ix];
        d->subscript.key = sub_data->data;
        d->subscript.length = (size_t)sub_data->length;
      }
      else
      {
        d->subscript = ADLB_NO_SUB;
      }

      assert(tmp[i].val_data >= 0 && tmp[i].val_data < extra_data_count);
      adlb_binary_data *data = &extra_data_ptrs[tmp[i].val_data];
      d->value = data->data;
      d->value_len = data->length;
    }
    notifs->references.count += added_count;
    free(tmp);
  }

  if (counts->refc_count > 0)
  {
    int refc_count = counts->refc_count;

    xlb_refc_changes *c = &notifs->refcs;
    TRACE("Receiving %i rc changes", refc_count);
    ac = xlb_refc_changes_expand(c, refc_count);
    ADLB_CHECK(ac);

    RECV(&c->arr[c->count],
         refc_count * (int)sizeof(c->arr[0]),
         MPI_BYTE, to_server_rank, ADLB_TAG_RESPONSE_NOTIF);

#if XLB_INDEX_REFC_CHANGES
    // Rebuild index
    //  - Merge new data into existing ones
    //  - Index remaining new data
    bool has_existing_counts = c->count > 0;
    for (int i = 0; i < refc_count; i++)
    {
      int change_ix = c->count + i;
      adlb_datum_id change_id = c->arr[change_ix].id;
      if (has_existing_counts &&
          xlb_refc_change_merge_existing(c, change_id,
            c->arr[change_ix].rc.read_refcount,
            c->arr[change_ix].rc.write_refcount,
            false)) {
        // Merged - move last one here
        c->arr[change_ix] =
          c->arr[c->count + refc_count - 1];
        i--;
        refc_count--;
      }
      else
      {
        bool added = table_lp_add(&c->index, change_id,
                                 (void*)(unsigned long)change_ix);
        CHECK_MSG(added, "Could not add to refcount index table");
      }
    }
#endif

    // Only extend to cover new ones now that we've finished
    // manipulating index
    notifs->refcs.count += refc_count;
  }

  TRACE("Done receiving notifs");
  return ADLB_SUCCESS;
}
