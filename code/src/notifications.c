#include "notifications.h"

#include "client_internal.h"
#include "common.h"
#include "handlers.h"
#include "messaging.h"
#include "refcount.h"
#include "server.h"
#include "sync.h"
#include "turbine.h"

#define MAX_NOTIF_PAYLOAD (32+ADLB_DATA_SUBSCRIPT_MAX)

static adlb_code
xlb_process_local_notif_ranks(adlb_notif_ranks *ranks);

static adlb_code
xlb_close_notify(const adlb_notif_ranks *ranks);

static adlb_code
xlb_rc_changes_apply(adlb_notif_t *notifs, bool apply_all,
                               bool apply_local, bool apply_preacquire);

static adlb_code
xlb_set_refs(adlb_notif_t *notifs, bool local_only);

static adlb_code
xlb_set_ref(adlb_datum_id id, adlb_subscript subscript,
          const void *value, int length, adlb_data_type type,
          adlb_refcounts transferred_refs, adlb_notif_t *notifs);

static adlb_code
xlb_notify_server(int server, adlb_datum_id id, adlb_subscript subscript);

static adlb_code
send_client_notif_work(int caller, 
        void *response, size_t response_len,
        struct packed_notif_counts *inner_struct,
        const adlb_notif_t *notifs, bool use_xfer);

// Returns size of payload including null terminator
static int fill_payload(char *payload, adlb_datum_id id, adlb_subscript subscript)
{
  int len_str;
  if (!adlb_has_sub(subscript))
  {
    len_str = sprintf(payload, "close %"PRId64"", id);
  }
  else
  {
    // TODO: support binary subscript
    len_str = sprintf(payload, "close %"PRId64" %.*s", id,
             (int)subscript.length, (const char*)subscript.key);
  }
  return len_str + 1;
}

static adlb_code notify_local(int target, const char *payload, int length)
{
  int answer_rank = -1;
  int work_prio = 1;
  int work_type = 1; // work_type CONTROL
  int rc = xlb_put_targeted_local(work_type, xlb_comm_rank,
               work_prio, answer_rank,
               target, payload, length);
  ADLB_CHECK(rc);
  return ADLB_SUCCESS;
}

static adlb_code notify_nonlocal(int target, int server,
                        const char *payload, int length)
{
  int answer_rank = -1;
  int work_prio = 1;
  int work_type = 1; // work_type CONTROL
  int rc;
  if (xlb_am_server)
  {
    rc = xlb_sync(server);
    ADLB_CHECK(rc);
  }
  rc = ADLB_Put(payload, length, target,
                    answer_rank, work_type, work_prio, 1);
  ADLB_CHECK(rc);
  return ADLB_SUCCESS;
}


void xlb_free_notif(adlb_notif_t *notifs)
{
  xlb_free_ranks(&notifs->notify);
  xlb_free_datums(&notifs->references);
  xlb_rc_changes_free(&notifs->rc_changes);
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
    if (!local_only || ADLB_Locate(ref->id) == xlb_comm_rank)
    {
      TRACE("Notifying reference %"PRId64" (%s)\n", ref->id,
            ADLB_Data_type_tostring(ref->type));
      rc = xlb_set_ref(ref->id, ref->subscript, ref->value,
                ref->value_len, ref->type, ref->refcounts, notifs);
      ADLB_CHECK(rc);
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
            const void *value, int length, adlb_data_type type,
            adlb_refcounts transferred_refs, adlb_notif_t *notifs)
{
  DEBUG("xlb_set_ref: <%"PRId64">[%.*s]=%p[%i] r: %i w: %i", id,
      (int)subscript.length, (const char*)subscript.key, value, length,
      transferred_refs.read_refcount, transferred_refs.write_refcount);

  int rc = ADLB_SUCCESS;
  int server = ADLB_Locate(id);

  if (server == xlb_comm_rank)
  {
    adlb_data_code dc = xlb_data_store(id, subscript, value, length,
                     type, ADLB_WRITE_RC, transferred_refs, notifs);
    ADLB_DATA_CHECK(dc);

    return ADLB_SUCCESS;
  }

  if (xlb_am_server)
    rc = xlb_sync(server);
  ADLB_CHECK(rc);

  // Store value, maybe accumulating more notification/ref setting work
  rc = xlb_store(id, ADLB_NO_SUB, type, value, length, ADLB_WRITE_RC,
                 transferred_refs, notifs);
  ADLB_CHECK(rc);
  TRACE("SET_REFERENCE DONE");
  return ADLB_SUCCESS;

}

static adlb_code
xlb_close_notify(const adlb_notif_ranks *ranks)
{
  adlb_code rc;
  char payload[MAX_NOTIF_PAYLOAD];
  int payload_len = 0;
  adlb_subscript last_subscript = ADLB_NO_SUB;

  for (int i = 0; i < ranks->count; i++)
  {
    adlb_notif_rank *notif = &ranks->notifs[i];
   
    int target = notif->rank;
    int server = xlb_map_to_server(target);

    if (server == target)
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
        payload_len = fill_payload(payload, notif->id, notif->subscript);
      }
      if (xlb_am_server && server == xlb_comm_rank)
      {
        rc = notify_local(target, payload, payload_len);
        ADLB_CHECK(rc);
      }
      else
      {
        rc = notify_nonlocal(target, server, payload, payload_len);
        ADLB_CHECK(rc);
      }
    }
  }
  return ADLB_SUCCESS;
}

static adlb_code
xlb_notify_server(int server, adlb_datum_id id, adlb_subscript subscript)
{
  MPI_Status status;
  MPI_Request request;
  // TODO: pack id/subscript
  // TODO: send to server
  // TODO: wait for response

  int subscript_len = adlb_has_sub(subscript) ? (int)subscript.length : 0;
  assert(subscript_len <= ADLB_DATA_SUBSCRIPT_MAX);

  // Stack allocate small buffer
  int hdr_len = (int)sizeof(struct packed_notify_hdr) + subscript_len;
  char hdr_buffer[hdr_len];
  struct packed_notify_hdr *hdr = (struct packed_notify_hdr*)hdr_buffer;

  // Fill in header
  hdr->id = id;
  hdr->subscript_len = subscript_len;
  if (subscript_len > 0)
  {
    memcpy(hdr->subscript, subscript.key, (size_t)subscript_len);
  }

  int response; 
  IRECV(&response, 1, MPI_INT, server, ADLB_TAG_RESPONSE);
  SEND(hdr, hdr_len, MPI_BYTE, server, ADLB_TAG_NOTIFY);
  WAIT(&request,&status);

  return (adlb_code)response;
}

adlb_code
xlb_process_local_notif(adlb_notif_t *notifs)
{
  assert(xlb_am_server);
  
  adlb_code ac;
  
  // First set references where necessary
  // This may result in adding some refcount changes
  ac = xlb_set_refs(notifs, true);
  ADLB_CHECK(ac);

  // Do local refcounts second, since they can add additional notifs
  // Also do pre-increments here, since we can't send them
  ac = xlb_rc_changes_apply(notifs, false, true, true);
  ADLB_CHECK(ac);

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
  assert(xlb_am_server);
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

      if (target == xlb_comm_rank)
      {
        /*
          Handle notification for self, which must have been initiated
          by a Turbine engine subscribe.  This may result in work being
          released.  This will be stored in xlb_server_ready_work to be
          later processed by the server loop
         */
        adlb_data_code dc;
        if (adlb_has_sub(notif->subscript))
        {
          dc = turbine_sub_close(notif->id, notif->subscript,
                                &xlb_server_ready_work);
          ADLB_DATA_CHECK(dc);
        }
        else
        {
          dc = turbine_close(notif->id, &xlb_server_ready_work);

          ADLB_DATA_CHECK(dc);
        }
        processed_locally = true;
      }
      else
      {
        // Check to see if target is worker belonging to server
        if (i == 0 || notif->subscript.key != last_subscript.key ||
                      notif->subscript.length != last_subscript.length)
        {
          // Skip refilling payload if possible 
          payload_len = fill_payload(payload, notif->id, notif->subscript);
        }

        int server = xlb_map_to_server(target);
        if (server == xlb_comm_rank)
        {
          // Swap with last and shorten array
          int rc = notify_local(target, payload, payload_len);
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
      free(ranks->notifs);
      ranks->notifs = NULL;
    }
  }
  return ADLB_SUCCESS;
}

adlb_code
xlb_notify_all(adlb_notif_t *notifs)
{
  adlb_code rc;
  if (notifs->rc_changes.count > 0)
  {
    // apply rc changes first because it may add new notifications
    rc = xlb_rc_changes_apply(notifs, true, true, true);
    ADLB_CHECK(rc);
  }

  assert(notifs->rc_changes.count == 0);

  if (notifs->notify.count > 0)
  {
    rc = xlb_close_notify(&notifs->notify);
    ADLB_CHECK(rc);
  }

  assert(notifs->notify.count == 0);
  assert(notifs->rc_changes.count == 0);

  if (!notifs->references.count > 0)
  {
    rc = xlb_set_refs(notifs, false);
    ADLB_CHECK(rc);
  }

  // Check all were cleared
  assert(xlb_notif_empty(notifs)); 
  return ADLB_SUCCESS;
}

/*
 * Apply refcount changes and remove entries from list.
 * This will add additional notifications to the notifs structure if
 *  freeing/closing data results in more notifications.
 * All rc changes in notif matching criteria will be removed, including any
 * that were added during processing
 * 
 * apply_all: if true, apply all changes
 * apply_local: if true, apply changes to local data
 * apply_preacquire: if true, apply changes that must be applied early
 */
static adlb_code
xlb_rc_changes_apply(adlb_notif_t *notifs, bool apply_all,
                               bool apply_local, bool apply_preacquire)
{
  adlb_data_code dc;
  xlb_rc_changes *c = &notifs->rc_changes;
  for (int i = 0; i < c->count; i++)
  {
    bool applied = false;
    xlb_rc_change *change = &c->arr[i];
    if (ADLB_RC_IS_NULL(change->rc))
    {
      // Don't need to apply null refcounts
      applied = true;
    }
    else if (apply_all || (apply_preacquire && change->must_preacquire))
    {
      // Apply reference count operation
      if (xlb_am_server)
      {
        // update reference count (can be local or remote)
        dc = xlb_incr_rc_svr(change->id, change->rc, notifs);
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
    else if (apply_local && ADLB_Locate(change->id) == xlb_comm_rank)
    {
      // Process locally and added consequential notifications to list
      dc = xlb_data_reference_count(change->id, change->rc, XLB_NO_ACQUIRE,
                                    NULL, notifs);
      ADLB_DATA_CHECK(dc);
      applied = true;
    }

    if (applied)
    {
#if XLB_INDEX_RC_CHANGES
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

      // Remove processed entries
      c->count--;
      if (i != c->count)
      {
        // Swap last to here
        *change = c->arr[c->count];
        i--; // Process new entry next
#if XLB_INDEX_RC_CHANGES
        if (maintain_index)
        {
          void *ptr;
          bool found = table_lp_set(&c->index, change->id,
                      (void*)(unsigned long)(i + 1), &ptr);
          assert(found);
          assert(((unsigned long)ptr) == c->count);
          TRACE("Reindex change for id <%"PRId64">: %lu => %d",
                change->id, (unsigned long)ptr, i);
        }
#endif
      }
    }
  }

  // Free memory if none present
  if (c->count == 0)
  {
    // Remove all from list
    xlb_rc_changes_free(c);
  }

  return ADLB_SUCCESS;
}

adlb_code
xlb_notifs_expand(adlb_notif_ranks *notifs, int to_add)
{
  assert(to_add >= 0);
  int new_size = notifs->size == 0 ? 
                    64 : notifs->size * 2;
  if (new_size < notifs->size + to_add)
    new_size = notifs->size + to_add;

  notifs->notifs = realloc(notifs->notifs, sizeof(notifs->notifs[0]) *
                       (size_t)new_size);
  ADLB_MALLOC_CHECK(notifs->notifs);
  notifs->size = new_size;
  return ADLB_SUCCESS;
}

adlb_code
xlb_refs_expand(adlb_ref_data *refs, int to_add)
{
  assert(to_add >= 0);
  int new_size = refs->size == 0 ? 
                    64 : refs->size * 2;
  if (new_size < refs->size + to_add)
    new_size = refs->size + to_add;

  refs->data = realloc(refs->data, sizeof(refs->data[0]) *
                       (size_t)new_size);
  ADLB_MALLOC_CHECK(refs->data);
  refs->size = new_size;
  return ADLB_SUCCESS;
}

adlb_code
xlb_to_free_expand(adlb_notif_t *notifs, int to_add)
{
  assert(to_add >= 0);
  size_t new_size = notifs->to_free_size == 0 ? 
                    64 : notifs->to_free_size * 2;
  if (new_size < notifs->to_free_size + (size_t)to_add)
    new_size = notifs->to_free_size + (size_t)to_add;

  notifs->to_free = realloc(notifs->to_free,
                    sizeof(notifs->to_free[0]) * new_size);
  ADLB_MALLOC_CHECK(notifs->to_free);
  notifs->to_free_size = new_size;
  return ADLB_SUCCESS;
}

adlb_code
xlb_send_notif_work(int caller,
        void *response, size_t response_len,
        struct packed_notif_counts *inner_struct,
        adlb_notif_t *notifs, bool use_xfer)
{
  adlb_code rc;
  if (ADLB_CLIENT_NOTIFIES)
  {

    // Remove any notifications that can be handled locally
    xlb_process_local_notif(notifs);
    
    // Send work back to client
    rc = send_client_notif_work(caller, response, response_len,
                                       inner_struct, notifs, use_xfer);
    ADLB_CHECK(rc);
  }
  else 
  {
    // Handle on server
    // Initialize counts to all zeroes
    memset(inner_struct, 0, sizeof(*inner_struct));
    RSEND(response, (int)response_len, MPI_BYTE, caller,
          ADLB_TAG_RESPONSE);
    rc = xlb_notify_all(notifs);
    ADLB_CHECK(rc);
  }

  return ADLB_SUCCESS;
}

static adlb_code
send_client_notif_work(int caller, 
        void *response, size_t response_len,
        struct packed_notif_counts *inner_struct,
        const adlb_notif_t *notifs, bool use_xfer)
{
  // will send remaining to client
  int notify_count = notifs->notify.count;
  int refs_count = notifs->references.count;
  int rc_count = notifs->rc_changes.count;
  assert(notify_count >= 0);
  assert(refs_count >= 0);
  assert(rc_count >= 0);

  /*
   We need to send subscripts and values back to client, so we pack them
   all into a buffer, and send them back.  We can then reference them by
   their index in the buffer.
   */
  adlb_data_code dc;

  /*
   * Allocate some scratch space on stack.
   */
  int tmp_buf_size = 1024;
  char tmp_buf_data[tmp_buf_size];
  adlb_buffer static_buf;
  if (use_xfer)
  {
    static_buf = xfer_buf;
  }
  else
  {
    static_buf.data = tmp_buf_data;
    static_buf.length = tmp_buf_size;
  }
  bool using_static_buf = true;
  
  adlb_buffer extra_data;
  int extra_pos = 0;

  dc = ADLB_Init_buf(&static_buf, &extra_data, &using_static_buf, 0);
  ADLB_DATA_CHECK(dc);

  int extra_data_count = 0;
  struct packed_notif *packed_notifs =
            malloc(sizeof(struct packed_notif) * (size_t)notify_count);
  ADLB_MALLOC_CHECK(packed_notifs);

  struct packed_reference *packed_refs =
            malloc(sizeof(struct packed_reference) * (size_t)refs_count);
  ADLB_MALLOC_CHECK(packed_refs);

  // Track last subscript so we don't send redundant subscripts
  const adlb_subscript *last_subscript = NULL;

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
        packed_notifs[i].subscript_data = extra_data_count - 1;
      }
      else
      {
        packed_notifs[i].subscript_data = extra_data_count;

        // pack into extra data
        dc = ADLB_Append_buffer(ADLB_DATA_TYPE_NULL,rank->subscript.key,
            (int)rank->subscript.length, true, &extra_data,
            &using_static_buf, &extra_pos);
        ADLB_DATA_CHECK(dc);
       
        last_subscript = &rank->subscript;
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
  int last_value_len;
  for (int i = 0; i < refs_count; i++)
  {
    adlb_ref_datum *ref = &notifs->references.data[i];
    packed_refs[i].id = ref->id;
    packed_refs[i].type = ref->type;
    packed_refs[i].refcounts = ref->refcounts;

    if (adlb_has_sub(ref->subscript))
    {
      packed_refs[i].subscript_data = extra_data_count++;
      dc = ADLB_Append_buffer(ADLB_DATA_TYPE_NULL, ref->subscript.key,
          (int)ref->subscript.length, true, &extra_data, &using_static_buf,
          &extra_pos);
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
      packed_refs[i].val_data = extra_data_count - 1;
    }
    else
    {
      packed_refs[i].val_data = extra_data_count;
      dc = ADLB_Append_buffer(ADLB_DATA_TYPE_NULL, ref->value,
          ref->value_len, true, &extra_data, &using_static_buf,
          &extra_pos);
      ADLB_DATA_CHECK(dc);
      
      last_value = ref->value;
      last_value_len = ref->value_len;
      extra_data_count++;
    }
  }

  // Fill in data and send response header
  inner_struct->notify_count = notify_count;
  inner_struct->reference_count = refs_count;
  inner_struct->rc_change_count = rc_count;
  inner_struct->extra_data_count = extra_data_count;
  inner_struct->extra_data_bytes = extra_pos;
  RSEND(response, (int)response_len, MPI_BYTE, caller, ADLB_TAG_RESPONSE);

  if (extra_pos > 0)
  {
    assert(extra_data_count > 0);
    SEND(extra_data.data, extra_pos, MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  }
  if (notify_count > 0)
  {
    SEND(packed_notifs, notify_count * (int)sizeof(packed_notifs[0]),
         MPI_BYTE, caller, ADLB_TAG_RESPONSE);
  }
  if (refs_count > 0)
  {
    SEND(packed_refs, refs_count * (int)sizeof(packed_refs[0]), MPI_BYTE,
         caller, ADLB_TAG_RESPONSE);
  }
  if (rc_count > 0)
  {
    DEBUG("Sending %i rc changes", rc_count);
    
    SEND(notifs->rc_changes.arr, 
         rc_count * (int)sizeof(notifs->rc_changes.arr[0]), MPI_BYTE,
         caller, ADLB_TAG_RESPONSE);
  }

  if (!using_static_buf)
  {
    free(extra_data.data);
  }
  
  free(packed_notifs);
  free(packed_refs);
  return ADLB_DATA_SUCCESS;
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
  MPI_Status status;

  void *extra_data = NULL;
  adlb_binary_data *extra_data_ptrs = NULL; // Pointers into buffer
  int extra_data_count = 0;
  if (counts->extra_data_bytes > 0)
  {
    int bytes = counts->extra_data_bytes;
    assert(bytes >= 0);
    if (bytes <= XFER_SIZE)
    {
      extra_data = xfer;
    }
    else
    {
      extra_data = malloc((size_t)bytes);
      ADLB_MALLOC_CHECK(extra_data);
    }

    RECV(extra_data, bytes, MPI_BYTE, to_server_rank, ADLB_TAG_RESPONSE);

    // Locate the separate data entries in the buffer
    extra_data_count = counts->extra_data_count;
    assert(extra_data_count >= 0);
    extra_data_ptrs = malloc(sizeof(extra_data_ptrs[0]) * (size_t)extra_data_count);
    ADLB_MALLOC_CHECK(extra_data_ptrs);
    int pos = 0;
    for (int i = 0; i < extra_data_count; i++)
    {
      ac = ADLB_Unpack_buffer(ADLB_DATA_TYPE_NULL, extra_data, bytes, &pos,
              &extra_data_ptrs[i].data, &extra_data_ptrs[i].length);
      ADLB_CHECK(ac);
    }
    assert(pos == bytes); // Should consume all of buffer
  }

  if (counts->notify_count > 0)
  {
    int added_count = counts->notify_count;
    ac = xlb_notifs_expand(&notifs->notify, added_count);
    ADLB_CHECK(ac);

    struct packed_notif *tmp = malloc(sizeof(struct packed_notif) *
                                             (size_t)added_count);
    ADLB_MALLOC_CHECK(tmp);

    RECV(tmp, (int)sizeof(tmp[0]) * added_count,
        MPI_BYTE, to_server_rank, ADLB_TAG_RESPONSE);

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
        assert(data->length >= 0);
        r->subscript.key = data->data;
        r->subscript.length = (size_t)data->length;
      }
    }
    notifs->notify.count += added_count;
    free(tmp);
  }

  if (counts->reference_count > 0)
  {
    int added_count = counts->reference_count;
    ac = xlb_refs_expand(&notifs->references, added_count);
    ADLB_CHECK(ac);

    struct packed_reference *tmp =
        malloc(sizeof(struct packed_reference) * (size_t)added_count);
    ADLB_MALLOC_CHECK(tmp);

    RECV(tmp, added_count * (int)sizeof(tmp[0]), MPI_BYTE,
         to_server_rank, ADLB_TAG_RESPONSE);

    for (int i = 0; i < added_count; i++)
    {
      // Copy data from tmp and fill in values
      adlb_ref_datum *d;
      d = &notifs->references.data[notifs->references.count + i];
      d->id = tmp[i].id;
      d->type = tmp[i].type;
      d->refcounts = tmp[i].refcounts;

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

  if (counts->rc_change_count > 0)
  {
    int rc_change_count = counts->rc_change_count;

    xlb_rc_changes *c = &notifs->rc_changes; 
    DEBUG("Receiving %i rc changes", rc_change_count);
    ac = xlb_rc_changes_expand(c, rc_change_count);
    ADLB_CHECK(ac);

    RECV(&c->arr[c->count], 
         rc_change_count * (int)sizeof(c->arr[0]),
         MPI_BYTE, to_server_rank, ADLB_TAG_RESPONSE);

#if XLB_INDEX_RC_CHANGES
    // Rebuild index
    //  - Merge new data into existing ones
    //  - Index remaining new data
    bool has_existing_counts = c->count > 0;
    for (int i = 0; i < rc_change_count; i++)
    {
      int change_ix = c->count + i;
      adlb_datum_id change_id = c->arr[change_ix].id;
      if (has_existing_counts &&
          xlb_rc_change_merge_existing(c, change_id, 
            c->arr[change_ix].rc.read_refcount,
            c->arr[change_ix].rc.write_refcount,
            false)) {
        // Merged - move last one here
        c->arr[change_ix] =
          c->arr[c->count + rc_change_count - 1];
        i--;
        rc_change_count--;
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
    notifs->rc_changes.count += rc_change_count;
  }

  if (extra_data != NULL && extra_data != xfer)
  {
    ac = xlb_to_free_add(notifs, extra_data);
    ADLB_CHECK(ac)
  }
  if (extra_data_ptrs != NULL)
  {
    ac = xlb_to_free_add(notifs, extra_data_ptrs);
    ADLB_CHECK(ac)
  }
  return ADLB_SUCCESS;
}
