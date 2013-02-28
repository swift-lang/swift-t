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


/**
 * ADLB Data module
 *
 * All functions return ADLB_DATA_SUCCESS or ADLB_DATA_ERROR_*
 * */

#ifndef DATA_H
#define DATA_H

#include "adlb-defs.h"

adlb_data_code data_init(int s, int r);

adlb_data_code data_typeof(adlb_datum_id id, adlb_data_type* type);

adlb_data_code data_container_typeof(adlb_datum_id id,
                                     adlb_data_type* type);

adlb_data_code data_create(adlb_datum_id id, adlb_data_type type,
                           const adlb_create_props *props);

adlb_data_code data_create_filename(adlb_datum_id id,
                                    const char* filename,
                                    const adlb_create_props *props);

adlb_data_code data_create_container(adlb_datum_id id,
                                     adlb_data_type type,
                                     const adlb_create_props *props);

void           data_exists(adlb_datum_id id, bool* result);

adlb_data_code data_lock(adlb_datum_id id, int rank, bool* result);

adlb_data_code data_unlock(adlb_datum_id id);

adlb_data_code data_subscribe(adlb_datum_id id, int rank,
                              int* result);

adlb_data_code data_container_reference(adlb_datum_id container_id,
                                        const char* subscript,
                                        adlb_datum_id reference,
                                        adlb_datum_id* member);

adlb_data_code data_container_reference_str(adlb_datum_id container_id,
                                        const char* subscript,
                                        adlb_datum_id reference,
                                        adlb_data_type ref_type,
                                        char **member);

adlb_data_code data_container_size(adlb_datum_id container_id,
                                   int* size);

adlb_data_code data_retrieve(adlb_datum_id id, adlb_data_type* type,
                             void** result, int* length);

adlb_data_code data_enumerate(adlb_datum_id container_id,
                              int count, int offset,
                              char** subscripts,
                              int* subscripts_length,
                              char** members,
                              int* members_length,
                              int* actual);

adlb_data_code data_store(adlb_datum_id id, void* buffer, int length,
          bool decr_write_refcount, int** notify_ranks, int* notify_count);

adlb_data_code data_reference_count(adlb_datum_id id, adlb_refcount_type type,
                int increment, int** notify_ranks, int* notify_count);

adlb_data_code data_insert(adlb_datum_id id,
                           char* subscript,
                           char* member,
                           int drops,
                           adlb_datum_id** references, int* references_count,
                           int **notify_ranks, int *notify_count);

adlb_data_code data_insert_atomic(adlb_datum_id container_id,
                                  const char* subscript,
                                  bool* result);

adlb_data_code data_lookup(adlb_datum_id id, const char* subscript,
                           char** result);

adlb_data_code data_unique(adlb_datum_id* result);

adlb_data_code data_finalize(void);

#endif
