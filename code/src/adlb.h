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


#ifndef ADLB_H
#define ADLB_H

// Need _GNU_SOURCE for asprintf()
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <stdarg.h>
#include <stdbool.h>
#include <limits.h>

#include "mpi.h"
#include "adlb-defs.h"

#include "version.h"

#define XLB
#define XLB_VERSION 0

/*
   These are the functions available to ADLB application code
 */

adlb_code ADLBP_Init(int nservers, int ntypes, int type_vect[],
                     int *am_server, MPI_Comm adlb_comm,
                     MPI_Comm *worker_comm);
adlb_code ADLB_Init(int nservers, int ntypes, int type_vect[],
                    int *am_server, MPI_Comm adlb_comm,
                    MPI_Comm *worker_comm);

adlb_code ADLB_Server(long max_memory);

adlb_code ADLB_Version(version* output);

adlb_code ADLB_Hostmap(const char* name, int count,
                       int* output, int* actual);

adlb_code ADLBP_Put(void* payload, int length, int target, int answer,
                    int type, int priority, int parallelism);
adlb_code ADLB_Put(void* payload, int length, int target, int answer,
                   int type, int priority, int parallelism);

adlb_code ADLBP_Get(int type_requested, void* payload, int* length,
                    int* answer, int* type_recvd, MPI_Comm* comm);
adlb_code ADLB_Get(int type_requested, void* payload, int* length,
                   int* answer, int* type_recvd, MPI_Comm* comm);

adlb_code ADLBP_Iget(int type_requested, void* payload, int* length,
                     int* answer, int* type_recvd);
adlb_code ADLB_Iget(int type_requested, void* payload, int* length,
                    int* answer, int* type_recvd);

/**
   Obtain server rank responsible for data id
 */
int ADLB_Locate(long id);

// Applications should not call these directly but
// should use the typed forms defined below
adlb_code ADLBP_Create(adlb_datum_id id, adlb_data_type type,
                       const char* filename,
                       adlb_data_type subscript_type, adlb_create_props props,
                       adlb_datum_id *new_id);
adlb_code ADLB_Create(adlb_datum_id id, adlb_data_type type,
                      const char* filename,
                      adlb_data_type subscript_type,
                      adlb_create_props props, adlb_datum_id *new_id);

// Information for new variable creation
typedef struct {
  adlb_datum_id id;
  adlb_data_type type;
  adlb_data_type subscript_type; // if required
  adlb_create_props props;
} ADLB_create_spec;

// Create multiple variables.
// Currently we assume that spec[i].id is ADLB_DATA_ID_NULL and
// will be filled in with a new id
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

adlb_code ADLB_Create_file(adlb_datum_id id, const char* filename,
                           adlb_create_props props, adlb_datum_id *new_id);

adlb_code ADLB_Create_container(adlb_datum_id id,
                                adlb_data_type subscript_type,
                                adlb_create_props props,
                                adlb_datum_id *new_id);

adlb_code ADLBP_Exists(adlb_datum_id id, bool* result);
adlb_code ADLB_Exists(adlb_datum_id id, bool* result);

adlb_code ADLBP_Store(adlb_datum_id id, void *data, int length,
                      bool decr_write_refcount, int** ranks, int *count);
adlb_code ADLB_Store(adlb_datum_id id, void *data, int length,
                      bool decr_write_refcount, int** ranks, int *count);

adlb_code ADLBP_Retrieve(adlb_datum_id id, adlb_data_type* type,
      int decr_read_refcount, void *data, int *length);
adlb_code ADLB_Retrieve(adlb_datum_id id, adlb_data_type* type,
      int decr_read_refcount, void *data, int *length);

adlb_code ADLBP_Enumerate(adlb_datum_id container_id,
                   int count, int offset,
                   char** subscripts, int* subscripts_length,
                   char** members, int* members_length,
                   int* records);
adlb_code ADLB_Enumerate(adlb_datum_id container_id,
                   int count, int offset,
                   char** subscripts, int* subscripts_length,
                   char** members, int* members_length,
                   int* records);

adlb_code ADLBP_Refcount_incr(adlb_datum_id id, adlb_refcount_type type,
                              int change);
adlb_code ADLB_Refcount_incr(adlb_datum_id id, adlb_refcount_type type,
                              int change);

adlb_code ADLBP_Insert(adlb_datum_id id, const char *subscript,
                 const char* member, int member_length, int drops);
adlb_code ADLB_Insert(adlb_datum_id id, const char *subscript,
                const char* member, int member_length, int drops);

adlb_code ADLBP_Insert_atomic(adlb_datum_id id, const char *subscript,
                        bool* result);
adlb_code ADLB_Insert_atomic(adlb_datum_id id, const char *subscript,
                       bool* result);

adlb_code ADLBP_Lookup(adlb_datum_id id, const char *subscript, char* member, int* found);
adlb_code ADLB_Lookup(adlb_datum_id id, const char *subscript, char* member, int* found);

adlb_code ADLBP_Subscribe(adlb_datum_id id, int* subscribed);
adlb_code ADLB_Subscribe(adlb_datum_id id, int* subscribed);

adlb_code ADLBP_Container_reference(adlb_datum_id id, const char *subscript,
                              adlb_datum_id reference,
                              adlb_data_type ref_type);
adlb_code ADLB_Container_reference(adlb_datum_id id, const char *subscript,
                             adlb_datum_id reference,
                              adlb_data_type ref_type);

adlb_code ADLBP_Unique(adlb_datum_id *result);
adlb_code ADLB_Unique(adlb_datum_id *result);

adlb_code ADLBP_Typeof(adlb_datum_id id, adlb_data_type* type);
adlb_code ADLB_Typeof(adlb_datum_id id, adlb_data_type* type);

adlb_code ADLBP_Container_typeof(adlb_datum_id id, adlb_data_type* type);
adlb_code ADLB_Container_typeof(adlb_datum_id id, adlb_data_type* type);

adlb_code ADLBP_Container_size(adlb_datum_id container_id, int* size);
adlb_code ADLB_Container_size(adlb_datum_id container_id, int* size);

adlb_code ADLBP_Lock(adlb_datum_id id, bool* result);
adlb_code ADLB_Lock(adlb_datum_id id, bool* result);

adlb_code ADLBP_Unlock(adlb_datum_id id);
adlb_code ADLB_Unlock(adlb_datum_id id);

void ADLB_Data_string_totype(const char* type_string,
                             adlb_data_type* type);

int ADLB_Data_type_tostring(char* output, adlb_data_type type);

adlb_code ADLB_Server_idle(int rank, bool* result);
adlb_code ADLB_Server_shutdown(int rank);

adlb_code ADLBP_Finalize(void);
adlb_code ADLB_Finalize(void);

adlb_code ADLB_Fail(int code);

void ADLB_Abort(int code);

#endif

