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


#pragma once

#include "adlb-defs.h"
#include "version.h"

#define XLB
#define XLB_VERSION 0

/*
   These are the functions available to ADLB application code
 */

adlb_code ADLBP_Init(int nservers, int ntypes, int type_vect[],
                     int* am_server, MPI_Comm adlb_comm,
                     MPI_Comm* worker_comm);
adlb_code ADLB_Init(int nservers, int ntypes, int type_vect[],
                    int* am_server, MPI_Comm adlb_comm,
                    MPI_Comm* worker_comm);

adlb_code ADLB_Server(long max_memory);

adlb_status ADLB_Status(void);

adlb_code ADLB_Version(version* output);

MPI_Comm ADLB_GetComm(void);

MPI_Comm ADLB_GetComm_workers(void);

MPI_Comm ADLB_GetComm_leaders(void);

void ADLB_Leaders(int* leaders, int* count);

adlb_code ADLB_Hostmap_stats(unsigned int* count,
                             unsigned int* name_max);

adlb_code ADLB_Hostmap_lookup(const char* name, int count,
                              int* output, int* actual);

/**
   Obtain RS-separated buffer of host names
   @param output: OUT Buffer into which to write result
   @param max: Maximal number of characters to write
   @param offset: start with this hostname
   @param actual: out number of hostnames written
 */
adlb_code ADLB_Hostmap_list(char* output, unsigned int max,
                            unsigned int offset, int* actual);

/*
  Put a task into the global task queue.

  @param payload: data buffer containing task data
  @param length: length of the payload in bytes
  @param target: target rank for task, adlb_rank_any if any target
  @param answer: answer rank passed to receiver of task
  @param type: task type
  @param priority: priority of task
  @param parallelism: number of ranks to execute task
              (1 for serial tasks, > 1 for parallel tasks)
  @param opts: additional options
 */
adlb_code ADLBP_Put(const void* payload, int length, int target, int answer,
                    int type, adlb_put_opts opts);
adlb_code ADLB_Put(const void* payload, int length, int target, int answer,
                   int type, adlb_put_opts opts);

/*
  Put a data-dependent task into the global task queue.  The task will
  be released and eligible to be matched to an ADLB_Get call once all
  specified ids reach write refcount 0, and all specified id/subscript
  pairs are assigned.  Most parameters are identical to adlb_put, except:
  @param wait_ids: array of ids to wait for
  @param wait_id_count: length of wait_ids array
  @param wait_id_subs: array of id/subscript pairs to wait for
  @param wait_id_sub_count: length of wait_id_subs array
 */
adlb_code ADLBP_Dput(const void* payload, int length, int target,
        int answer, int type, adlb_put_opts opts, const char *name,
        const adlb_datum_id *wait_ids, int wait_id_count,
        const adlb_datum_id_sub *wait_id_subs, int wait_id_sub_count);
adlb_code ADLB_Dput(const void* payload, int length, int target,
        int answer, int type, adlb_put_opts opts, const char *name,
        const adlb_datum_id *wait_ids, int wait_id_count,
        const adlb_datum_id_sub *wait_id_subs, int wait_id_sub_count);

/*
  Get a task from the global task queue.
  @param type_requested: the type of work requested
  @param payload IN/OUT Pointer into which to receive task
                        May be changed if too small, in which case
                        caller must free the new value
                        Caller should compare payload before and after
  @param length IN/OUT original initial/actual length of payload
  @param length IN Limit for allocating new payload
  @param answer OUT parameter for answer rank specified in ADLB_Put
                    for task
  @param type_recvd OUT parameter for actual type of task
  @param comm   OUT parameter for MPI communicator to use for
                executing parallel task
 */
adlb_code ADLBP_Get(int type_requested, void** payload,
                    int* length, int max_length,
                    int* answer, int* type_recvd, MPI_Comm* comm);
adlb_code ADLB_Get(int type_requested, void** payload,
                   int* length, int max_length,
                   int* answer, int* type_recvd, MPI_Comm* comm);

/*
 Polling equivalent of ADLB_Get.  Returns ADLB_NOTHING if no
 matching task are available.  Other return codes are same as
 ADLB_Get

  NOTE: Iget does not currently support parallel tasks
*/
adlb_code ADLBP_Iget(int type_requested, void* payload, int* length,
                     int* answer, int* type_recvd, MPI_Comm* comm);
adlb_code ADLB_Iget(int type_requested, void* payload, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm);
/*
  Non-blocking equivalent of ADLB_Get.  Matching requests should be
  filled in in the order that they are posted (i.e. if work matches two
  requests from a client, the first request that was posted will be
  filled first.

  If a work unit doesn't fit in the posted buffer, a runtime error
  will occur.

  TODO: currently we assume that only requests for the same type
        will be issues concurrently
  payload: will be retained by ADLB until request is completed.
  req: handle used to check for completion, filled in by function
 */
adlb_code ADLBP_Aget(int type_requested, adlb_payload_buf payload,
                     adlb_get_req *req);
adlb_code ADLB_Aget(int type_requested, adlb_payload_buf payload,
                     adlb_get_req *req);

/*
  Same as ADLB_Aget except initiates multiple requests at once.

  nreqs: number of requests to initiate
  wait: wait for first request to be filled (or shutdown to occur),
       Return immediately if 0 requests.
       After returns, ADLB_Aget_wait will immediately succeed
       on first request returned.
  payloads: array of nreqs payload buffers
  reqs: array of nreqs requests, filled in with request handles
 */
adlb_code ADLBP_Amget(int type_requested, int nreqs, bool wait,
                     const adlb_payload_buf* payloads,
                     adlb_get_req *reqs);
adlb_code ADLB_Amget(int type_requested, int nreqs, bool wait,
                     const adlb_payload_buf* payloads,
                     adlb_get_req *reqs);

/*
  Test if a get request completed without blocking.

  Return codes match ADLB_Get
 */
adlb_code ADLBP_Aget_test(adlb_get_req *req, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm);
adlb_code ADLB_Aget_test(adlb_get_req *req, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm);

/*
  Wait until a get request completes.
  Return codes match ADLB_Get
 */
adlb_code ADLBP_Aget_wait(adlb_get_req *req, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm);
adlb_code ADLB_Aget_wait(adlb_get_req *req, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm);

/**
   Obtain server rank responsible for data id
 */
int ADLB_Locate(adlb_datum_id id);

// Applications should not call these directly but
// should use the typed forms defined below
// Can be called locally on server where data resides
adlb_code ADLBP_Create(adlb_datum_id id, adlb_data_type type,
                       adlb_type_extra type_extra,
                       adlb_create_props props,
                       adlb_datum_id *new_id);
adlb_code ADLB_Create(adlb_datum_id id, adlb_data_type type,
                      adlb_type_extra type_extra,
                      adlb_create_props props, adlb_datum_id *new_id);

// Create multiple variables.
// Currently we assume that spec[i].id is ADLB_DATA_ID_NULL and
// will be filled in with a new id
// Can be called locally on server where data resides
adlb_code ADLB_Multicreate(ADLB_create_spec *specs, int count);
adlb_code ADLBP_Multicreate(ADLB_create_spec *specs, int count);


adlb_code ADLB_Create_integer(adlb_datum_id id, adlb_create_props props,
                              adlb_datum_id *new_id);

adlb_code ADLB_Create_float(adlb_datum_id id, adlb_create_props props,
                              adlb_datum_id *new_id);

adlb_code ADLB_Create_string(adlb_datum_id id, adlb_create_props props,
                              adlb_datum_id *new_id);

adlb_code ADLB_Create_blob(adlb_datum_id id, adlb_create_props props,
                              adlb_datum_id *new_id);

adlb_code ADLB_Create_ref(adlb_datum_id id, adlb_create_props props,
                              adlb_datum_id *new_id);
/**
 * Struct type: specify struct type, or leave as ADLB_STRUCT_TYPE_NULL to
 *              resolve upon assigning typed struct value
 */
adlb_code ADLB_Create_struct(adlb_datum_id id, adlb_create_props props,
                             adlb_struct_type struct_type, adlb_datum_id *new_id);

adlb_code ADLB_Create_container(adlb_datum_id id,
                                adlb_data_type key_type,
                                adlb_data_type val_type,
                                adlb_create_props props,
                                adlb_datum_id *new_id);

adlb_code ADLB_Create_multiset(adlb_datum_id id,
                                adlb_data_type val_type,
                                adlb_create_props props,
                                adlb_datum_id *new_id);
/*
  Add debug symbol entry, overwriting any existing entry.
  Only adds to local table (not on other ranks).

  symbol: debug symbol identifier, should not be ADLB_DSYM_NULL
  data: associated null-terminated data string, will be copied.
 */
adlb_code ADLBP_Add_dsym(adlb_dsym symbol, adlb_dsym_data data);
adlb_code ADLB_Add_dsym(adlb_dsym symbol, adlb_dsym_data data);

/*
  Retrieve debug symbol entry from local debug symbol table.

  symbol: a debug symbol identifier
  return: entry previous added for symbol, or NULL values if not present
 */
adlb_dsym_data ADLBP_Dsym(adlb_dsym symbol);
adlb_dsym_data ADLB_Dsym(adlb_dsym symbol);

adlb_code ADLBP_Exists(adlb_datum_id id, adlb_subscript subscript, bool* result,
                       adlb_refc decr);
adlb_code ADLB_Exists(adlb_datum_id id, adlb_subscript subscript, bool* result,
                       adlb_refc decr);

/**
 * Find out the current reference counts for a datum.
 *
 * E.g. if you want to find out that the datum is closed with refcount 0
 * This will succeed with zero refcounts if datum isn't found.
 *
 * result: refcounts of id after decr applied
 * decr: amount to decrement refcounts
 */
adlb_code ADLBP_Refcount_get(adlb_datum_id id, adlb_refc *result,
                              adlb_refc decr);
adlb_code ADLB_Refcount_get(adlb_datum_id id, adlb_refc *result,
                              adlb_refc decr);

/*
  Store value into datum
  data: binary representation
  length: length of binary representation
  refcount_decr: refcounts to to decrement on this id
  store_refcounts: refcounts to include for any refs in this data
  returns: ADLB_SUCCESS if store succeeded
           ADLB_REJECTED if id/subscript already assigned and cannot be
                         overwritten
           ADLB_ERROR for other errors
 */
adlb_code ADLBP_Store(adlb_datum_id id, adlb_subscript subscript,
          adlb_data_type type, const void *data, size_t length,
          adlb_refc refcount_decr, adlb_refc store_refcounts);
adlb_code ADLB_Store(adlb_datum_id id, adlb_subscript subscript,
          adlb_data_type type, const void *data, size_t length,
          adlb_refc refcount_decr, adlb_refc store_refcounts);

/*
   Retrieve contents of datum.

   refcounts: specify how reference counts should be changed
      read_refcount: decrease read refcount of this datum
      incr_read_referand: increase read refcount of referands,
                to ensure they aren't cleaned up prematurely
   type: output arg for the type of the datum
   data: a buffer of at least size ADLB_DATA_MAX
   length: output arg for data size in bytes
 */
adlb_code ADLBP_Retrieve(adlb_datum_id id, adlb_subscript subscript,
      adlb_retrieve_refc refcounts,
      adlb_data_type* type, void* data, size_t* length);
adlb_code ADLB_Retrieve(adlb_datum_id id, adlb_subscript subscript,
      adlb_retrieve_refc refcounts, adlb_data_type* type,
      void* data, size_t* length);

/*
   List contents of container

   data: binary encoded keys and values (if requested), with each member
          encoded as follows:
        - key length encoded with vint_encode()
        - key data without null terminator
        - value length encoded with vint_encode()
        - value data encoded with ADLB_Pack
   length: number of bytes of data
   records: number of elements returned
 */
adlb_code ADLBP_Enumerate(adlb_datum_id container_id,
                   int count, int offset, adlb_refc decr,
                   bool include_keys, bool include_vals,
                   void** data, size_t* length, int* records,
                   adlb_type_extra *kv_type);
adlb_code ADLB_Enumerate(adlb_datum_id container_id,
                   int count, int offset, adlb_refc decr,
                   bool include_keys, bool include_vals,
                   void** data, size_t* length, int* records,
                   adlb_type_extra *kv_type);

// Switch on read refcounting and memory management, which is off by default
adlb_code ADLBP_Read_refcount_enable(void);
adlb_code ADLB_Read_refcount_enable(void);

adlb_code ADLBP_Refcount_incr(adlb_datum_id id, adlb_refc change);
adlb_code ADLB_Refcount_incr(adlb_datum_id id, adlb_refc change);

/*
  Try to reserve an insert position in container
  result: true if could be created, false if already present/reserved
  value_present: true if value was present (not just reserved)
  data: optionally, if this is not NULL, return the existing value in
        this buffer of at least size ADLB_DATA_MAX
  length: length of existing value if found
  type: type of existing value
  refcounts: refcounts to apply.
        if data exists, apply all refcounts
        if placeholder exists, don't apply
        if nothing exists, don't apply
 */
adlb_code ADLBP_Insert_atomic(adlb_datum_id id, adlb_subscript subscript,
                        adlb_retrieve_refc refcounts,
                        bool *result, bool *value_present,
                        void *data, size_t *length, adlb_data_type *type);
adlb_code ADLB_Insert_atomic(adlb_datum_id id, adlb_subscript subscript,
                        adlb_retrieve_refc refcounts,
                        bool *result, bool *value_present,
                        void *data, size_t *length, adlb_data_type *type);

/*
  returns: ADLB_SUCCESS if datum found
       ADLB_NOTHING if datum not found (can indicate it was gced)
 */
adlb_code ADLBP_Subscribe(adlb_datum_id id, adlb_subscript subscript,
                          int work_type, int* subscribed);
adlb_code ADLB_Subscribe(adlb_datum_id id, adlb_subscript subscript,
                          int work_type, int* subscribed);

adlb_code ADLBP_Container_reference(adlb_datum_id id, adlb_subscript subscript,
                adlb_datum_id ref_id, adlb_subscript ref_subscript,
                adlb_data_type ref_type, adlb_refc transfer_refs,
                int ref_write_decr);
adlb_code ADLB_Container_reference(adlb_datum_id id, adlb_subscript subscript,
                adlb_datum_id ref_id, adlb_subscript ref_subscript,
                adlb_data_type ref_type, adlb_refc transfer_refs,
                int ref_write_decr);

/*
 * Allocate a unique data ID
 */
adlb_code ADLBP_Unique(adlb_datum_id *result);
adlb_code ADLB_Unique(adlb_datum_id *result);

/*
 * Allocates a range of count data IDs ADLB ranks.  This is useful for
 * implementing, for example, globally shared variables.  The allocated
 * range is inclusive of start and end and will have count members, i.e.
 * is [start, start + count).
 *
 * Must be called collectively by all ranks of the ADLB communicator.
 * The same range will be returned on all ranks.  This will return
 * once all globals have been correctly set up.
 *
 * Start and end are arbitrarily selected by the function.
 * The allocated IDs will be negative, so will not conflict with any
 * user-defined IDs in the positive range or IDs allocated by ADLB_Unique
 * or a ADLB_Create or related call.  If the allocated range conflicts
 * with any IDs already created, this will return an error.
 */
adlb_code ADLBP_Alloc_global(int count, adlb_datum_id *start);
adlb_code ADLB_Alloc_global(int count, adlb_datum_id *start);

adlb_code ADLBP_Typeof(adlb_datum_id id, adlb_data_type* type);
adlb_code ADLB_Typeof(adlb_datum_id id, adlb_data_type* type);

adlb_code ADLBP_Container_typeof(adlb_datum_id id, adlb_data_type* key_type,
                                 adlb_data_type* val_type);
adlb_code ADLB_Container_typeof(adlb_datum_id id, adlb_data_type* key_type,
                                 adlb_data_type* val_type);

adlb_code ADLBP_Container_size(adlb_datum_id container_id, int* size,
                               adlb_refc decr);
adlb_code ADLB_Container_size(adlb_datum_id container_id, int* size,
                              adlb_refc decr);

adlb_code ADLBP_Lock(adlb_datum_id id, bool* result);
adlb_code ADLB_Lock(adlb_datum_id id, bool* result);

adlb_code ADLBP_Unlock(adlb_datum_id id);
adlb_code ADLB_Unlock(adlb_datum_id id);

/**
  Get information about a type based on name.
  Returns error if not found
 */
adlb_code ADLB_Data_string_totype(const char* type_string,
              adlb_data_type* type, adlb_type_extra *extra);

const char *ADLB_Data_type_tostring(adlb_data_type type);

/*
  Convert string to placement enum value.
  Case insensitive.
 */
adlb_code ADLB_string_to_placement(const char *string,
                           adlb_placement *placement);

adlb_code ADLB_Server_idle(int rank, int64_t check_attempt, bool* result,
                 int *request_counts, int *untargeted_work_counts);

adlb_code ADLBP_Finalize(void);
adlb_code ADLB_Finalize(void);

/**
   Tell server to fail.
 */
adlb_code ADLB_Fail(int code);

void ADLB_Abort(int code);
