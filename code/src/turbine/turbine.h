/*
 * turbine.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef TURBINE_H
#define TURBINE_H

#include "src/util/longlist.h"
#include "src/turbine/defs.h"

typedef long turbine_transform_id;
typedef struct
{
  char* name;
  char* action;
  int inputs;
  turbine_datum_id* input;
  int outputs;
  turbine_datum_id* output;
} turbine_transform;

#define TURBINE_MAX_ENTRY 256

turbine_code turbine_init(int amserver);

turbine_code turbine_declare(turbine_datum_id id,
                             struct longlist** result);

turbine_code turbine_rule_add(turbine_transform_id id,
                              turbine_transform* transform);

turbine_code turbine_rule_new(turbine_transform_id *id);

turbine_code turbine_rules_push(void);

turbine_code turbine_ready(int count, turbine_transform_id* output,
                           int *result);

turbine_code turbine_close(turbine_datum_id id);

turbine_code turbine_action(turbine_transform_id id,
                              char* action);

turbine_code turbine_complete(turbine_transform_id id);

int turbine_code_tostring(char* output, turbine_code code);

int turbine_data_tostring(char* output, int length,
                          turbine_datum_id id);

void turbine_finalize(void);

#endif
