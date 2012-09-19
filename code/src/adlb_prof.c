
/*
   ADLB Profiling Interface
 */

#include "adlb.h"
#include "common.h"
#include "data.h"

/*
  When guessing the user state, we assume that the user is processing
  a work unit of a given type if they have done a Get for that type.
*/

#ifdef ENABLE_MPE

#include <mpe.h>

#include "mpe-tools.h"

static int my_log_rank;

// User work type events:

/** Previous work type from Get.  -1 indicates nothing */
// static int user_type_previous = -1;
/** Currently running work type from Get.  -1 indicates nothing */
static int user_type_current = -1;
/** Array of user state start events, one for each type */
static int* user_state_start;
/** Array of user state end events, one for each type */
static int* user_state_end;
// static int *user_types;

static char user_state_description[256];
#endif

static void setup_mpe_events(int num_types, int* types);

adlb_code
ADLB_Init(int num_servers, int num_types, int* types,
          int* am_server, MPI_Comm* app_comm)
{

  // In XLB, the types must be in simple order
  for (int i = 0; i < num_types; i++)
    if (types[i] != i)
    {
      printf("ADLB_Init(): types must be in order: 0,1,2...\n");
      MPI_Abort(MPI_COMM_WORLD,1);
    }

  MPE(xlb_mpe_setup());
  setup_mpe_events(num_types, types);

  MPE(MPE_Log_event(xlb_mpe_init_start, 0, NULL));
  int rc = ADLBP_Init(num_servers, num_types, types, am_server,
                      app_comm);
  MPE(MPE_Log_event(xlb_mpe_init_end, 0, NULL));

  return rc;
}

/**
   Sets up user_state events
   This does nothing if MPE is not enabled
 */
static void
setup_mpe_events(int num_types, int* types)
{
#ifdef ENABLE_MPE
  PMPI_Comm_rank(MPI_COMM_WORLD,&my_log_rank);

  user_state_start = malloc(num_types * sizeof(int));
  user_state_end   = malloc(num_types * sizeof(int));
  int user_num_types   = num_types;
  for (int i = 0; i < num_types; i++)
  {
    MPE_Log_get_state_eventIDs(&user_state_start[i],
                               &user_state_end[i]);
    if ( my_log_rank == 0 )
    {
      sprintf(user_state_description,"user_state_%d", types[i]);
      MPE_Describe_state(user_state_start[i], user_state_end[i],
                         user_state_description, "MPE_CHOOSE_COLOR");
    }
  }
#endif
}

adlb_code
ADLB_Put(void *work_buf, int work_len, int reserve_rank,
         int answer_rank, int work_type, int work_prio)
{
  int rc;

#ifdef ENABLE_MPE
  MPE_Log_event(xlb_mpe_wkr_put_start,0,NULL);
#endif

  rc = ADLBP_Put(work_buf,work_len,reserve_rank,answer_rank,
                 work_type,work_prio);

#ifdef ENABLE_MPE
  MPE_Log_event(xlb_mpe_wkr_put_end,0,NULL);
#endif

  return rc;
}

#ifdef ENABLE_MPE

/**
   Log that this worker is working on the given work type
   If type == -1, the worker is not working
 */
static inline void
mpe_log_user_state(int type)
{
  if (type == -1)
  {
    // Starting a new Get() - ending user state
    if (user_type_current != -1)
    {
      // We have a valid previous state to end (not first get())
      int i = xlb_type_index(user_type_current);
      MPE_Log_bare_event(user_state_end[i]);
    }
  }
  else
  {
    // Just completed a Get() - starting user state
    user_type_current = type;
    int i = xlb_type_index(user_type_current);
    MPE_Log_bare_event(user_state_start[i]);
  }

  user_type_current = type;
}

#endif

adlb_code
ADLB_Get(int type_requested, void* payload, int* length,
         int* answer, int* type_recvd)
{
#ifdef ENABLE_MPE
  mpe_log_user_state(-1);
  MPE_Log_bare_event(xlb_mpe_wkr_get_start);
#endif

  int rc = ADLBP_Get(type_requested, payload, length, answer,
                     type_recvd);

#ifdef ENABLE_MPE
  MPE_Log_bare_event(xlb_mpe_wkr_get_end);
  if (rc == ADLB_SUCCESS)
    mpe_log_user_state(*type_recvd);
#endif

  return rc;
}

/**
   Applications should use the ADLB_Create_type macros in adlb.h
 */
adlb_code ADLB_Create(long id, adlb_data_type type,
                const char* filename,
                adlb_data_type container_type)
{
  return ADLBP_Create(id, type, filename, container_type);
}

adlb_code ADLB_Exists(adlb_datum_id id, bool* result)
{
    int rc = ADLBP_Exists(id, result);

    return rc;
}

adlb_code ADLB_Store(adlb_datum_id id, void *data, int length)
{
#ifdef ENABLE_MPE
    MPE_Log_event(xlb_mpe_wkr_store_start, 0, NULL);
#endif

    int rc = ADLBP_Store(id, data, length);

#ifdef ENABLE_MPE
    MPE_Log_event(xlb_mpe_wkr_store_end, 0, NULL);
#endif
    return rc;
}

adlb_code ADLB_Retrieve(adlb_datum_id id, adlb_data_type* type,
		  void *data, int *length)
{
#ifdef ENABLE_MPE
    MPE_Log_event(xlb_mpe_wkr_retrieve_start, 0, NULL);
#endif

    int rc = ADLBP_Retrieve(id, type, data, length);

#ifdef ENABLE_MPE
    MPE_Log_event(xlb_mpe_wkr_retrieve_end, 0, NULL);
#endif

    return rc;
}

adlb_code
ADLB_Enumerate(adlb_datum_id container_id,
               int count, int offset,
               char** subscripts, int* subscripts_length,
               char** members, int* members_length,
               int* records)
{
  return ADLBP_Enumerate(container_id, count, offset,
                         subscripts, subscripts_length,
                         members, members_length, records);
}

adlb_code
ADLB_Slot_create(adlb_datum_id id)
{
  return ADLBP_Slot_create(id);
}

adlb_code
ADLB_Slot_drop(adlb_datum_id id)
{
  return ADLBP_Slot_drop(id);
}

adlb_code ADLB_Insert(adlb_datum_id id, const char *subscript,
                const char* member, int member_length,
                int drops)
{
  return ADLBP_Insert(id, subscript, member, member_length, drops);
}

adlb_code ADLB_Insert_atomic(adlb_datum_id id, const char *subscript,
                       bool* result)
{
  int rc = ADLBP_Insert_atomic(id, subscript, result);
  return rc;
}

adlb_code ADLB_Lookup(adlb_datum_id id, const char *subscript, char* member, int* found)
{
  return ADLBP_Lookup(id, subscript, member, found);
}

adlb_code ADLB_Unique(adlb_datum_id *result)
{
  return ADLBP_Unique(result);
}

adlb_code ADLB_Typeof(adlb_datum_id id, adlb_data_type* type)
{
  return ADLBP_Typeof(id, type);
}

adlb_code ADLB_Container_typeof(adlb_datum_id id, adlb_data_type* type)
{
  return ADLBP_Container_typeof(id, type);
}

adlb_code ADLB_Subscribe(adlb_datum_id id, int* subscribed)
{
  return  ADLBP_Subscribe(id, subscribed);
}

adlb_code ADLB_Container_reference(adlb_datum_id id, const char *subscript,
                             adlb_datum_id reference)
{
  return ADLBP_Container_reference(id, subscript, reference);
}

adlb_code ADLB_Container_size(adlb_datum_id id, int* size)
{
  int rc;
  rc = ADLBP_Container_size(id, size);
  return rc;
}

adlb_code ADLB_Close(adlb_datum_id id, int** ranks, int* count)
{
    return ADLBP_Close(id, ranks, count);
}

adlb_code
ADLB_Lock(adlb_datum_id id, bool* result)
{
  return ADLBP_Lock(id, result);
}

adlb_code
ADLB_Unlock(adlb_datum_id id)
{
  return ADLBP_Unlock(id);
}

