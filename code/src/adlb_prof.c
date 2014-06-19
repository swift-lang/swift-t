/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */


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

#include "mpe-tools.h"

#ifdef ENABLE_MPE

#include <mpe.h>

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
          int* am_server, MPI_Comm comm, MPI_Comm* worker_comm)
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

  MPE_LOG(xlb_mpe_all_init_start);
  int rc = ADLBP_Init(num_servers, num_types, types, am_server,
                      comm, worker_comm);
  MPE_LOG(xlb_mpe_all_init_end);

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
ADLB_Put(const void *work_buf, int work_len, int reserve_rank,
         int answer_rank, int work_type, int work_prio,
         int parallelism)
{
  MPE_LOG(xlb_mpe_wkr_put_start);

  int rc = ADLBP_Put(work_buf, work_len, reserve_rank, answer_rank,
                     work_type, work_prio, parallelism);

  MPE_LOG(xlb_mpe_wkr_put_end);

  return rc;
}

adlb_code ADLB_Dput(const void* payload, int length, int target,
        int answer, int type, int priority, int parallelism,
        const char *name,
        const adlb_datum_id *wait_ids, int wait_id_count, 
        const adlb_datum_id_sub *wait_id_subs, int wait_id_sub_count)
{
  MPE_LOG(xlb_mpe_wkr_put_rule_start);

  int rc = ADLBP_Dput(payload, length, target, answer,
                     type, priority, parallelism, name,
                     wait_ids, wait_id_count, wait_id_subs, wait_id_sub_count);

  MPE_LOG(xlb_mpe_wkr_put_rule_end);

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
         int* answer, int* type_recvd, MPI_Comm* comm)
{
#ifdef ENABLE_MPE
  mpe_log_user_state(-1);
#endif
  MPE_LOG(xlb_mpe_wkr_get_start);

  int rc = ADLBP_Get(type_requested, payload, length, answer,
                     type_recvd, comm);

  MPE_LOG(xlb_mpe_wkr_get_end);
#ifdef ENABLE_MPE
  if (rc == ADLB_SUCCESS)
    mpe_log_user_state(*type_recvd);
#endif

  return rc;
}

adlb_code
ADLB_Iget(int type_requested, void* payload, int* length,
         int* answer, int* type_recvd)
{
  MPE_LOG(xlb_mpe_wkr_iget_start);
  adlb_code rc = ADLBP_Iget(type_requested, payload, length, answer,
                            type_recvd);
  MPE_LOG(xlb_mpe_wkr_iget_end);
  return rc;
}

adlb_code
ADLB_Aget(int type_requested, adlb_payload_buf payload, adlb_get_req *req)
{
  MPE_LOG(xlb_mpe_wkr_aget_start);
  adlb_code rc = ADLBP_Aget(type_requested, payload, req);
  MPE_LOG(xlb_mpe_wkr_aget_end);
  return rc;
}

adlb_code
ADLB_Amget(int type_requested, int nreqs, const adlb_payload_buf* payloads,
          adlb_get_req *reqs)
{
  MPE_LOG(xlb_mpe_wkr_amget_start);
  adlb_code rc = ADLBP_Amget(type_requested, nreqs, payloads, reqs);
  MPE_LOG(xlb_mpe_wkr_amget_end);
  return rc;
}

adlb_code ADLB_Aget_test(adlb_get_req *req)
{
  MPE_LOG(xlb_mpe_wkr_aget_test_start);
  adlb_code rc = ADLBP_Aget_test(req);
  MPE_LOG(xlb_mpe_wkr_aget_test_end);
  return rc;
}

adlb_code ADLB_Aget_wait(adlb_get_req *req)
{
  MPE_LOG(xlb_mpe_wkr_aget_wait_start);
  adlb_code rc = ADLBP_Aget_wait(req);
  MPE_LOG(xlb_mpe_wkr_aget_wait_end);
  return rc;
}

/**
   Applications should use the ADLB_Create_type functions in adlb.h
 */
adlb_code
ADLB_Create(adlb_datum_id id, adlb_data_type type,
            adlb_type_extra type_extra,
            adlb_create_props props,
            adlb_datum_id *new_id)
{
  MPE_LOG(xlb_mpe_wkr_create_start);
  adlb_code rc = ADLBP_Create(id, type, type_extra, props, new_id);
  MPE_LOG(xlb_mpe_wkr_create_end);
  return rc;
}

adlb_code ADLB_Multicreate(ADLB_create_spec *specs, int count)
{
//   MPE_LOG(xlb_mpe_wkr_multicreate_start);
  adlb_code rc = ADLBP_Multicreate(specs, count);
//   MPE_LOG(xlb_mpe_wkr_multicreate_end);
  return rc;
}

adlb_code ADLB_Add_dsym(adlb_dsym symbol,
                                adlb_dsym_data data)
{
  return ADLBP_Add_dsym(symbol, data);
}

adlb_dsym_data ADLB_Dsym(adlb_dsym symbol)
{
  return ADLBP_Dsym(symbol);
}

adlb_code
ADLB_Exists(adlb_datum_id id, adlb_subscript subscript, bool* result,
            adlb_refc decr)
{
  MPE_LOG(xlb_mpe_wkr_exists_start);
  int rc = ADLBP_Exists(id, subscript, result, decr);
  return rc;
  MPE_LOG(xlb_mpe_wkr_exists_end);
}

adlb_code ADLB_Refcount_get(adlb_datum_id id, adlb_refc *result,
                              adlb_refc decr)
{
  MPE_LOG(xlb_mpe_wkr_get_refcounts_start);
  return ADLBP_Refcount_get(id, result, decr);
  MPE_LOG(xlb_mpe_wkr_get_refcounts_end);
}

adlb_code
ADLB_Store(adlb_datum_id id, adlb_subscript subscript,
          adlb_data_type type, const void *data, int length,
          adlb_refc refcount_decr, adlb_refc store_refcounts)
{
  MPE_LOG(xlb_mpe_wkr_store_start);
  int rc = ADLBP_Store(id, subscript, type, data, length, refcount_decr,
                       store_refcounts);
  MPE_LOG(xlb_mpe_wkr_store_end);
  return rc;
}

adlb_code
ADLB_Retrieve(adlb_datum_id id, adlb_subscript subscript,
      adlb_retrieve_refc refcounts, adlb_data_type* type,
      void *data, int *length)
{
  MPE_LOG(xlb_mpe_wkr_retrieve_start);
  adlb_code rc = ADLBP_Retrieve(id, subscript, refcounts, type, data, length);
  MPE_LOG(xlb_mpe_wkr_retrieve_end);
  return rc;
}

adlb_code
ADLB_Enumerate(adlb_datum_id container_id,
               int count, int offset, adlb_refc decr,
               bool include_keys, bool include_vals,
               void** data, int* length, int* records,
               adlb_type_extra *kv_type)
{
  return ADLBP_Enumerate(container_id, count, offset, decr,
                         include_keys, include_vals,
                         data, length, records, kv_type);
}

adlb_code
ADLB_Read_refcount_enable(void)
{
  return ADLBP_Read_refcount_enable();
}

adlb_code
ADLB_Refcount_incr(adlb_datum_id id, adlb_refc change)
{
  return ADLBP_Refcount_incr(id, change);
}

adlb_code ADLB_Insert_atomic(adlb_datum_id id, adlb_subscript subscript,
                       adlb_retrieve_refc refcounts,
                       bool* result, void *data, int *length,
                       adlb_data_type *type)
{
  adlb_code rc = ADLBP_Insert_atomic(id, subscript, refcounts, result,
                                     data, length, type);
  return rc;
}

adlb_code ADLB_Unique(adlb_datum_id *result)
{
  return ADLBP_Unique(result);
}

adlb_code ADLB_Typeof(adlb_datum_id id, adlb_data_type* type)
{
  return ADLBP_Typeof(id, type);
}

adlb_code ADLB_Container_typeof(adlb_datum_id id, adlb_data_type* key_type,
                                adlb_data_type* val_type)
{
  return ADLBP_Container_typeof(id, key_type, val_type);
}

adlb_code
ADLB_Subscribe(adlb_datum_id id, adlb_subscript subscript,
               int work_type, int* subscribed)
{
  MPE_LOG(xlb_mpe_wkr_subscribe_start);
  adlb_code rc = ADLBP_Subscribe(id, subscript, work_type, subscribed);
  MPE_LOG(xlb_mpe_wkr_subscribe_end);
  return rc;
}

adlb_code ADLB_Container_reference(adlb_datum_id id, adlb_subscript subscript,
               adlb_datum_id ref_id, adlb_subscript ref_subscript,
               adlb_data_type ref_type, adlb_refc transfer_refs)
{
  return ADLBP_Container_reference(id, subscript, ref_id,
                  ref_subscript, ref_type, transfer_refs);
}

adlb_code ADLB_Container_size(adlb_datum_id id, int* size,
                              adlb_refc decr)
{
  adlb_code rc;
  rc = ADLBP_Container_size(id, size, decr);
  return rc;
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

adlb_code
ADLB_Finalize()
{
  MPE_LOG(xlb_mpe_all_finalize_start);
  adlb_code rc = ADLBP_Finalize();
  MPE_LOG(xlb_mpe_all_finalize_end);
  // Safely write log before exiting
  MPE(MPE_Finish_log("adlb"));
  return rc;
}
