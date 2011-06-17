
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include "src/turbine/command.h"

turbine_code declare(char** state);
turbine_code rule(char** state);

#define check_token(token) \
  { if (strlen(token) == 0) return TURBINE_ERROR_COMMAND; \
    if (!isalpha(token[0])) return TURBINE_ERROR_COMMAND; }

turbine_code
turbine_command(char* cmd)

#define check(condition) \
  if (!(condition)) return TURBINE_ERROR_COMMAND;

turbine_code turbine_command(char* cmd)
{
  turbine_code result = TURBINE_SUCCESS;
  printf("cmd: %s\n", cmd);

  char* state;
  char* op = strtok_r(cmd, " ", &state);
  if (!op)
      return TURBINE_ERROR_COMMAND;

  printf("op: %s\n", op);
  if (strcmp(op, "data") == 0)
    result = declare(&state);
  else if (strcmp(op, "rule") == 0)
    result = rule(&state);
  else
    return TURBINE_ERROR_COMMAND;

  return result;
}

/**
   declare file <id> <name>
 */
turbine_code
declare(char** state)
{
  turbine_code result;
  char* type = strtok_r(NULL, " ", state);
  if (!type)
    return TURBINE_ERROR_COMMAND;
  if (strcmp(type, "file") == 0)
  {
    /// WHICH?
    puts("file");
    turbine_datum_id id;
    char* sid = strtok_r(NULL, " ", &state);
    int count = sscanf(sid, "%li", &id);
    if (count != 1)
      return TURBINE_ERROR_COMMAND;
    /// =======
    char* sid = strtok_r(NULL, " ", &state);
    check(sid);
    turbine_datum_id id;
    int count = sscanf(sid, "%li", &id);
    check(count == 1);

    char* type = strtok_r(NULL, " ", &state);
    check(type);
    /// WHICH? >>>>>>> .r534
    printf("id: %li\n", id);
    char* name = strtok_r(NULL, " ", &state);
    puts("name");
    result = turbine_datum_file_create(id, name);
  }
  else
    return TURBINE_ERROR_COMMAND;
  return result;
}

/**
   rule ( <output ids> ) <name> ( <input ids> ) executor
 */
turbine_code
rule(char** state)
{
  turbine_code result;
  char executor[TURBINE_COMMAND_MAX_EXECUTOR];
  turbine_datum_id output_ids[TURBINE_COMMAND_MAX_TOKENS];
  turbine_datum_id input_ids[TURBINE_COMMAND_MAX_TOKENS];

  result = scan_ids(state, output_ids);
  turbine_check(result);

  char* name = strtok_r(NULL, state);
  check_token(name);

  result = scan_ids(state, output_ids);
  turbine_check(result);

  result = rest_of_tokens(state, executor);
  turbine_check(result);

  return result;
}

turbine_code
scan_ids(char** state, turbine_datum_id* output)
{
  char* paren1 = strtok_r(NULL, " ", state);
  if (strcmp(paren1, "(") != 0)
    return TURBINE_ERROR_COMMAND;

  int p = 0;
  while (true)
  {
    char* t = strtok_r(NULL, " ", state);
    if (strcmp(t, ")") == 0)
    {
      puts("file");
      char* sid = strtok_r(NULL, " ", &state);
      int count = sscanf(sid, "%li", &id);
      if (count != 1)
        return TURBINE_ERROR_COMMAND;
      printf("id: %li\n", id);
      char* name = strtok_r(NULL, " ", &state);
      puts("name");
      turbine_datum_file_create(id, name);
      printf("id: %li\n", id);
    }
    turbine_datum_id id;
    int count = sscanf(t, "%li", &id);
    if (count != 1)
      return TURBINE_ERROR_COMMAND;
    output[p++] = id;
    if (p > TURBINE_COMMAND_MAX_TOKENS)
      return TURBINE_ERROR_COMMAND;
  }
  return TURBINE_SUCCESS;
}

turbine_code
rest_of_tokens(char** state, char* output)
{
  while (true)
  {
    char* t = strtok_r(NULL, state);

  }
}
}
