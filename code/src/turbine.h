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
 *      Author: wozniak
 *
 * TODO: performance counters for this module
 * */

#ifndef TURBINE_H
#define TURBINE_H

#include <mpi.h>

#include <turbine-defs.h>

#include "workqueue.h"

typedef struct {
  char *key;
  size_t length;
} turbine_subscript;

static const turbine_subscript TURBINE_NO_SUB = { .key = NULL, .length = 0 };

typedef struct {
  adlb_datum_id td;
  turbine_subscript subscript;
} td_sub_pair;

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

/*
  Convert code to string.
  output: buffer of at least TURBINE_CODE_STRING_MAX bytes
 */
int turbine_engine_code_tostring(char* output, turbine_engine_code code);

void turbine_engine_finalize(void);

// Temporary hack to get working ok
// TODO: move elsewhere
#include "debug.h"
#define DEBUG_TURBINE(s, args...) DEBUG("ENGINE:" s, ## args)

#endif
