
#include <stdio.h>
#include <string.h>

#include "src/turbine/command.h"

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
  {
    char* sid = strtok_r(NULL, " ", &state);
    check(sid);
    turbine_datum_id id;
    int count = sscanf(sid, "%li", &id);
    check(count == 1);

    char* type = strtok_r(NULL, " ", &state);
    check(type);
    if (strcmp(type, "file") == 0)
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
    else
      return TURBINE_ERROR_COMMAND;
  }
  if (strcmp(op, "rule") == 0)
  {

  }
  else
    return TURBINE_ERROR_COMMAND;

  return result;
}
