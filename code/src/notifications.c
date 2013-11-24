#include "notifications.h"

#include "common.h"
#include "handlers.h"
#include "server.h"
#include "sync.h"

#define MAX_NOTIF_PAYLOAD (32+ADLB_DATA_SUBSCRIPT_MAX)

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
  }
}

void xlb_free_datums(adlb_ref_data *datums)
{
  if (datums->data != NULL)
  {
    free(datums->data);
    datums->data = NULL;
  }
}


/*
   Set references.
   refs: an array of ids, where negative ids indicate that
          the value should be treated as a string, and
          positive indicates it should be parsed to integer
   value: string value to set references to.
 */
adlb_code xlb_set_refs(const adlb_ref_data *refs)
{
  adlb_code rc;
  for (int i = 0; i < refs->count; i++)
  {
    const adlb_ref_datum *ref = &refs->data[i];
    TRACE("Notifying reference %"PRId64" (%s)\n", ref->id,
          ADLB_Data_type_tostring(ref->type));
    rc = xlb_set_ref_and_notify(ref->id, ref->value, ref->value_len,
                                ref->type);
    ADLB_CHECK(rc);
  }
  return ADLB_SUCCESS;
}

adlb_code
xlb_close_notify(adlb_datum_id id, const adlb_notif_ranks *ranks)
{
  adlb_code rc;
  char payload[MAX_NOTIF_PAYLOAD];
  int payload_len = 0;
  adlb_subscript last_subscript = ADLB_NO_SUB;

  for (int i = 0; i < ranks->count; i++)
  {
    adlb_notif_rank *notif = &ranks->notifs[i];
    
    if (i == 0 || notif->subscript.key != last_subscript.key ||
                  notif->subscript.length != last_subscript.length)
    {
      // Skip refilling payload if possible 
      payload_len = fill_payload(payload, id, notif->subscript);
    }
    int target = notif->rank;
    int server = xlb_map_to_server(target);
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
  return ADLB_SUCCESS;
}

adlb_code
xlb_process_local_notif(adlb_datum_id id, adlb_notif_ranks *ranks)
{
  assert(xlb_am_server);
  if (ranks->count == 0)
    return ADLB_SUCCESS;

  char payload[MAX_NOTIF_PAYLOAD];
  int payload_len = 0;
  adlb_subscript last_subscript = ADLB_NO_SUB;

  int i = 0;
  while (i < ranks->count)
  {
    adlb_notif_rank *notif = &ranks->notifs[i];
    
    if (i == 0 || notif->subscript.key != last_subscript.key ||
                  notif->subscript.length != last_subscript.length)
    {
      // Skip refilling payload if possible 
      payload_len = fill_payload(payload, id, notif->subscript);
    }

    int target = notif->rank;
    int server = xlb_map_to_server(target);
    if (server == xlb_comm_rank)
    {
      // Swap with last and shorten array
      int rc = notify_local(target, payload, payload_len);
      ADLB_CHECK(rc);
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
  return ADLB_SUCCESS;
}

adlb_code
xlb_set_ref_and_notify(adlb_datum_id id, const void *value, int length,
                         adlb_data_type type)
{
  DEBUG("xlb_set_ref: <%"PRId64">=%p[%i]", id, value, length);

  int rc = ADLB_SUCCESS;
  int server = ADLB_Locate(id);
  if (xlb_am_server && server != xlb_comm_rank)
    rc = xlb_sync(server);
  ADLB_CHECK(rc);

  rc = ADLB_Store(id, ADLB_NO_SUB, type, value, length, ADLB_WRITE_RC);
  ADLB_CHECK(rc);
  TRACE("SET_REFERENCE DONE");
  return ADLB_SUCCESS;
}

adlb_code
xlb_notify_all(const adlb_notif_t *notifs, adlb_datum_id id)
{
  adlb_code rc;
  if (notifs->notify.count > 0)
  {
    rc = xlb_close_notify(id, &notifs->notify);
    ADLB_CHECK(rc);
  }
  if (notifs->references.count > 0)
  {
    rc = xlb_set_refs(&notifs->references);
    ADLB_CHECK(rc);
  } 
  return ADLB_SUCCESS;
}
