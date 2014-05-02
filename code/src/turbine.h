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
 *  TURBINE
 *
 *  Created on: May 4, 2011
 *  Moved to ADLB codebase: Apr 2014
 *      Authors: wozniak, armstrong
 *
 * Data dependency engine to manage release of tasks.  This module was
 * migrated and adapted from the Turbine engine code, and moved into
 * the ADLB server.
 */

#ifndef TURBINE_H
#define TURBINE_H

#include <mpi.h>

#include "workqueue.h"

typedef struct
{
  char* name;
} turbine_entry;

typedef enum
{
  TURBINE_SUCCESS = ADLB_DATA_SUCCESS,
  /** Out of memory */
  TURBINE_ERROR_OOM = ADLB_DATA_ERROR_OOM,
  /** Invalid input */
  TURBINE_ERROR_INVALID = ADLB_DATA_ERROR_INVALID,
  /** Called function when Turbine uninitialized */
  TURBINE_ERROR_UNINITIALIZED,
  /** Unknown error */
  TURBINE_ERROR_UNKNOWN = ADLB_DATA_ERROR_UNKNOWN,
} turbine_engine_code;

/**
   The maximal length of a Turbine rule name string
 */
#define TURBINE_NAME_MAX 18

/*
 * Array of ready work
 */
typedef struct {
  xlb_work_unit **work;
  int size; // allocated size
  int count; // entry count
} turbine_work_array;

turbine_engine_code turbine_engine_init(int rank);

void turbine_engine_print_counters(void);

/**
   input_td_list, input_td_sub_list:
        ownership of arrays and array contents is retained by caller
   work: ownership of this task is passed into the engine module
            until released
   ready: if true, rule is ready to run, and ownership stays with
          caller
   returns TURBINE_SUCCESS/TURBINE_ERROR_*
 */
turbine_engine_code turbine_rule(const char* name, int name_strlen,
                          int input_tds,
                          const adlb_datum_id* input_td_list,
                          int input_td_subs,
                          const adlb_datum_id_sub* input_td_sub_list,
                          xlb_work_unit *work, bool *ready);

/*
  Should be called when turbine engine is notified that an id is closed
  ready: array to append with pointers to any newly ready tasks,
          ownership of pointers add is passed to caller
 */
turbine_engine_code turbine_close(adlb_datum_id id,
                           turbine_work_array *ready);

/*
  Should be called when turbine engine is notified that an id/subscript
  is closed
  ready: array to append with pointers to any newly ready tasks,
          ownership of pointers add is passed to caller
 */
turbine_engine_code turbine_sub_close(adlb_datum_id id, adlb_subscript sub, 
                               turbine_work_array *ready);

#define TURBINE_CODE_STRING_MAX 64

void turbine_engine_finalize(void);

// Temporary hack to get working ok
// TODO: move elsewhere
#include "debug.h"
#define DEBUG_TURBINE(s, args...) DEBUG("ENGINE:" s, ## args)

#endif
