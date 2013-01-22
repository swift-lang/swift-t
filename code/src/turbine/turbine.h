
/**
 *  TURBINE
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 * */

#ifndef TURBINE_H
#define TURBINE_H

#include <version.h>

#include "src/turbine/turbine-defs.h"

typedef enum
{
  /** Act locally */
  TURBINE_ACTION_LOCAL = 1,
  /** Act on a remote engine */
  TURBINE_ACTION_CONTROL = 2,
  /** Act on a worker */
  TURBINE_ACTION_WORK = 3
} turbine_action_type;

typedef long turbine_transform_id;

turbine_code turbine_init(int amserver, int rank, int size);

turbine_code turbine_engine_init(void);

void turbine_version(version* output);

turbine_code turbine_rule(const char* name,
                          int inputs,
                          const turbine_datum_id* input_list,
                          turbine_action_type action_type,
                          const char* action,
                          int priority,
                          int target,
                          turbine_transform_id* id);

turbine_code turbine_rules_push(void);

/**
   Obtain the list of TRs ready to run
   @param count: maximum number to return
   @param output: location to store TR IDs
   @param result: number of TR IDs returned
 */
turbine_code turbine_ready(int count, turbine_transform_id* output,
                           int *result);

turbine_code turbine_close(turbine_datum_id id);

turbine_code turbine_pop(turbine_transform_id id,
                         turbine_action_type* action_type,
                         char* action, int* priority, int* target);

int turbine_code_tostring(char* output, turbine_code code);

void turbine_finalize(void);

#endif
