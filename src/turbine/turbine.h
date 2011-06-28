/*
 * turbine.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef TURBINE_H
#define TURBINE_H

typedef enum
{
  TURBINE_SUCCESS,
  /** Out of memory */
  TURBINE_ERROR_OOM,
  /** Attempt to declare the same thing twice */
  TURBINE_ERROR_DOUBLE_DECLARE,
  /** Attempt to set the same datum twice */
  TURBINE_ERROR_DOUBLE_WRITE,
  /** Data set not found */
  TURBINE_ERROR_NOT_FOUND,
  /** Bad string command given to the interpreter */
  TURBINE_ERROR_COMMAND,
  /** Unknown error */
  TURBINE_ERROR_UNKNOWN
} turbine_code;

typedef long turbine_transform_id;
typedef long turbine_datum_id;

typedef struct
{
  char* name;
  char* executor;
  int inputs;
  turbine_datum_id* input;
  int outputs;
  turbine_datum_id* output;
} turbine_transform;

typedef enum
{
  TURBINE_ENTRY_KEY,
  TURBINE_ENTRY_FIELD
} turbine_entry_type;

#define TURBINE_MAX_ENTRY 256

typedef struct
{
  turbine_entry_type type;
  char name[TURBINE_MAX_ENTRY];
}

#define TURBINE_ID_NULL 0

turbine_code turbine_init(void);

turbine_code turbine_datum_file_create(turbine_datum_id id,
                                       char* path);

turbine_code turbine_datum_container_create(turbine_datum_id id);

turbine_code turbine_filename(turbine_datum_id id,
                              char* output);

turbine_code turbine_lookup(turbine_datum_id id,
                            turbine_entry* entry,
                            turbine_datum_id* result);

turbine_code turbine_rule_add(turbine_transform_id id,
                              turbine_transform* transform);

turbine_code turbine_new(turbine_datum_id* id);

turbine_code turbine_rules_push(void);

turbine_code turbine_ready(int count, turbine_transform_id* output,
                           int *result);

turbine_code turbine_close(turbine_datum_id id);

turbine_code turbine_executor(turbine_transform_id id,
                              char* executor);

turbine_code turbine_complete(turbine_transform_id id);

int turbine_code_tostring(char* output, turbine_code code);

int turbine_data_tostring(char* output, int length,
                          turbine_datum_id id);

void turbine_finalize(void);

// Internal API:
#define turbine_check(code) if (code != TURBINE_SUCCESS) return code;

#define turbine_check_verbose(code) \
    turbine_check_impl(code, __FILE__, __LINE__)

#define turbine_check_impl(code, file, line)    \
    {                                           \
      if (code != TURBINE_SUCCESS)              \
      {                                         \
        char output[64];                        \
        turbine_code_tostring(output, code);    \
        printf("turbine error: %s\n", output);  \
        printf("\tat: %s:%i\n", file, line);    \
        return code;                            \
      }                                         \
    }

#endif
