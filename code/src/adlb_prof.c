
/*
 * ADLB Profiling Interface
 */

#include "adlb.h"
#include "data.h"

/* we either log adlb internals or a guess at user state */
/* when guessing the user state, we assume that the user is processing
 * a piece of work of a given type if they have done a Get_reserved
 * for that type.  We also assume they do the Get_reserverd for the most
 * recent Reserve.
 */

#ifdef ENABLE_MPE

#include <mpe.h>

static int my_log_rank;
// Events:
static int inita, initb, puta, putb, reservea, reserveb, ireservea, ireserveb,
           geta, getb, getat, getbt, nomoreworka, nomoreworkb,
           beginbatchputa, beginbatchputb, endbatchputa, endbatchputb,
           finalizea, finalizeb, probea, probeb;
// Data module:
static int storea, storeb, retrievea, retrieveb;
// Server events:
adlb_code mpe_svr_put_start, mpe_svr_put_end;
adlb_code mpe_svr_create_start, mpe_svr_create_end;
adlb_code mpe_svr_store_start, mpe_svr_store_end;
adlb_code mpe_svr_retrieve_start, mpe_svr_retrieve_end;
adlb_code mpe_svr_subscribe_start, mpe_svr_subscribe_end;
adlb_code mpe_svr_close_start, mpe_svr_close_end;
adlb_code mpe_svr_unique_start, mpe_svr_unique_end;
adlb_code mpe_svr_reserve_start, mpe_svr_reserve_end;
adlb_code mpe_svr_get_start, mpe_svr_get_end;

static int user_prev_type, user_curr_type, user_num_types,
           *user_state_start, *user_state_end, *user_types;
static int log_user_state_first_time = 1;
static char user_state_descr[256];
#endif

adlb_code
ADLB_Init(int num_servers, int num_types, int *types,
              int *am_server, MPI_Comm *app_comm)
{
  int rc;

#ifdef ENABLE_MPE
    int i;
#endif

#ifdef ENABLE_MPE
    PMPI_Comm_rank(MPI_COMM_WORLD,&my_log_rank);
#endif

    /* MPE_Init_log() & MPE_Finish_log() are NOT needed when liblmpe.a is linked
       because MPI_Init() would have called MPE_Init_log() already.
    */
#ifdef ENABLE_MPE
    MPE_Init_log();
#endif

#ifdef ENABLE_MPE
    MPE_Log_get_state_eventIDs(&inita,&initb);
    MPE_Log_get_state_eventIDs(&puta,&putb);
    MPE_Log_get_state_eventIDs(&reservea,&reserveb);
    MPE_Log_get_state_eventIDs(&ireservea,&ireserveb);
    MPE_Log_get_state_eventIDs(&geta,&getb);
    MPE_Log_get_state_eventIDs(&getat,&getbt);
    MPE_Log_get_state_eventIDs(&beginbatchputa,&beginbatchputb);
    MPE_Log_get_state_eventIDs(&endbatchputa,&endbatchputb);
    MPE_Log_get_state_eventIDs(&nomoreworka,&nomoreworkb);
    MPE_Log_get_state_eventIDs(&finalizea,&finalizeb);
    MPE_Log_get_state_eventIDs(&probea,&probeb);
    // Data module:
    MPE_Log_get_state_eventIDs(&storea,&storeb);
    MPE_Log_get_state_eventIDs(&retrievea,&retrieveb);
    // Server:
    MPE_Log_get_state_eventIDs(&mpe_svr_put_start, &mpe_svr_put_end);
    MPE_Log_get_state_eventIDs(&mpe_svr_create_start, &mpe_svr_create_end);
    MPE_Log_get_state_eventIDs(&mpe_svr_store_start, &mpe_svr_store_end);
    MPE_Log_get_state_eventIDs(&mpe_svr_retrieve_start, &mpe_svr_retrieve_end);
    MPE_Log_get_state_eventIDs(&mpe_svr_subscribe_start, &mpe_svr_subscribe_end);
    MPE_Log_get_state_eventIDs(&mpe_svr_close_start, &mpe_svr_close_end);
    MPE_Log_get_state_eventIDs(&mpe_svr_unique_start, &mpe_svr_unique_end);
    MPE_Log_get_state_eventIDs(&mpe_svr_reserve_start, &mpe_svr_reserve_end);
    MPE_Log_get_state_eventIDs(&mpe_svr_get_start, &mpe_svr_get_end);

    if ( my_log_rank == 0 ) {
        MPE_Describe_state( inita, initb, "ADLB_Init", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( puta, putb, "ADLB_Put", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( reservea, reserveb, "ADLB_Reserve", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( ireservea, ireserveb, "ADLB_Ireserve", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( geta, getb, "ADLB_Get", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( getat, getbt, "ADLB_GetTimed", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( nomoreworka, nomoreworkb, "ADLB_NoMoreWork", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( beginbatchputa, endbatchputb, "ADLB_BatchPut", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( finalizea, finalizeb, "ADLB_Finalize", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( probea, probeb, "adlb_Probe", "MPE_CHOOSE_COLOR" );
        // Data module:
        MPE_Describe_state( storea, storeb, "ADLB_Store", "MPE_CHOOSE_COLOR" );
        MPE_Describe_state( retrievea, retrieveb, "ADLB_Retrieve", "MPE_CHOOSE_COLOR" );
        // Server events:
        MPE_Describe_state(mpe_svr_put_start, mpe_svr_put_end, "ADLB_SVR_Put", "MPE_CHOOSE_COLOR");
        MPE_Describe_state(mpe_svr_create_start, mpe_svr_create_end, "ADLB_SVR_Create", "MPE_CHOOSE_COLOR");
        MPE_Describe_state(mpe_svr_store_start, mpe_svr_store_end, "ADLB_SVR_Store", "MPE_CHOOSE_COLOR");
        MPE_Describe_state(mpe_svr_subscribe_start, mpe_svr_subscribe_end, "ADLB_SVR_Subscribe", "MPE_CHOOSE_COLOR");
        MPE_Describe_state(mpe_svr_close_start, mpe_svr_close_end, "ADLB_SVR_Close", "MPE_CHOOSE_COLOR");
        MPE_Describe_state(mpe_svr_unique_start, mpe_svr_unique_end, "ADLB_SVR_Unique", "MPE_CHOOSE_COLOR");
        MPE_Describe_state(mpe_svr_reserve_start, mpe_svr_reserve_end, "ADLB_SVR_Reserve", "MPE_CHOOSE_COLOR");
        MPE_Describe_state(mpe_svr_get_start, mpe_svr_get_end, "ADLB_SVR_Get", "MPE_CHOOSE_COLOR");
    }

#endif

#ifdef ENABLE_MPE
    MPE_Log_event(inita,0,NULL);
#endif

#ifdef ENABLE_MPE
    user_state_start = malloc(num_types * sizeof(int) );
    user_state_end   = malloc(num_types * sizeof(int) );
    user_types       = malloc(num_types * sizeof(int) );
    user_num_types   = num_types;
    for (i=0; i < num_types; i++)
    {
        user_types[i] = types[i];
        MPE_Log_get_state_eventIDs(&user_state_start[i],&user_state_end[i]);
        if ( my_log_rank == 0 )
        {
            sprintf(user_state_descr,"user_state_%d",types[i]);
            MPE_Describe_state( user_state_start[i], user_state_end[i],
                                user_state_descr, "MPE_CHOOSE_COLOR" );
        }
    }
#endif

    rc = ADLBP_Init(num_servers, num_types, types, am_server,
                    app_comm);

#ifdef ENABLE_MPE
    MPE_Log_event(initb,0,NULL);
#endif

    return rc;
}

adlb_code ADLB_Server(long max_memory)
{
    int rc;
    rc = ADLBP_Server(max_memory);
    return rc;
}

adlb_code
ADLB_Put(void *work_buf, int work_len, int reserve_rank,
         int answer_rank, int work_type, int work_prio)
{
  int rc;

#ifdef ENABLE_MPE
  MPE_Log_event(puta,0,NULL);
#endif

  rc = ADLBP_Put(work_buf,work_len,reserve_rank,answer_rank,
                 work_type,work_prio);

#ifdef ENABLE_MPE
  MPE_Log_event(putb,0,NULL);
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
    MPE_Log_event(storea,0,NULL);
#endif

    int rc = ADLBP_Store(id, data, length);

#ifdef ENABLE_MPE
    MPE_Log_event(storeb,0,NULL);
#endif
    return rc;
}

adlb_code ADLB_Retrieve(adlb_datum_id id, adlb_data_type* type,
		  void *data, int *length)
{
#ifdef ENABLE_MPE
    MPE_Log_event(retrievea,0,NULL);
#endif

    int rc = ADLBP_Retrieve(id, type, data, length);

#ifdef ENABLE_MPE
    MPE_Log_event(retrieveb,0,NULL);
#endif

    return rc;
}

adlb_code ADLB_Enumerate(adlb_datum_id container_id,
                   int count, int offset,
                   char** subscripts, int* subscripts_length,
                   char** members, int* members_length,
                   int* records)
{
  return ADLBP_Enumerate(container_id, count, offset,
                         subscripts, subscripts_length,
                         members, members_length, records);
}

adlb_code ADLB_Slot_create(adlb_datum_id id)
{
  return ADLBP_Slot_create(id);
}

adlb_code ADLB_Slot_drop(adlb_datum_id id)
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

adlb_code ADLB_Lock(adlb_datum_id id, bool* result)
{
    return ADLBP_Lock(id, result);
}

adlb_code ADLB_Unlock(adlb_datum_id id)
{
    return ADLBP_Unlock(id);
}

adlb_code
ADLB_Get(int type_requested, void* payload, int* length,
         int* answer, int* type_recvd)
{
  int rc;

#ifdef ENABLE_MPE
  MPE_Log_event(reservea,0,NULL);
#endif

  rc = ADLBP_Get(type_requested, payload, length, answer, type_recvd);

#ifdef ENABLE_MPE
  MPE_Log_event(reserveb,0,NULL);
#endif

#ifdef ENABLE_MPE
  user_prev_type = user_curr_type;
  user_curr_type = *work_type;
#endif

  return rc;
}

adlb_code
ADLB_Finalize()
{
    int rc;

#ifdef ENABLE_MPE
    int i;
#endif

#ifdef ENABLE_MPE
    MPE_Log_event(finalizea,0,NULL);
#endif

    rc = ADLBP_Finalize();

#ifdef ENABLE_MPE
    MPE_Log_event(finalizeb,0,NULL);
#endif

#ifdef ENABLE_MPE
    if ( ! log_user_state_first_time)
    {
        for (i=0; i < user_num_types; i++)
            if (user_prev_type == user_types[i])
                break;
        if (i >= user_num_types)
        {
            aprintf(1,"** invalid type while logging: %d\n",user_prev_type);
            ADLBP_Abort(-1);
        }
        MPE_Log_event(user_state_end[i],user_prev_type,NULL);
    }
#endif

#if ENABLE_MPE
    MPE_Finish_log( "adlb" );
#endif

    return rc;
}

adlb_code ADLB_Abort(int code)
{
    int rc;
    rc = ADLBP_Abort(code);
    return rc;
}
