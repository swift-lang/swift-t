
#include <stdio.h>
#include <string.h>

#include "src/turbine/command.h"

// declare file <id> <name>
// rule ( <output ids> ) <name> ( <input ids> ) executor
turbine_code turbine_command(char* cmd)
{
  turbine_code result = TURBINE_SUCCESS;
  printf("cmd: %s\n", cmd);

  char* state;
  char* op = strtok_r(cmd, " ", &state);
  if (!op)
      return TURBINE_ERROR_COMMAND;

  printf("op: %s\n", op);
  if (strcmp(op, "declare") == 0)
  {
    puts("de");
    char* type = strtok_r(NULL, " ", &state);
    if (!type)
      return TURBINE_ERROR_COMMAND;
    if (strcmp(type, "file") == 0)
    {
      puts("file");
      turbine_datum_id id;
      char* sid = strtok_r(NULL, " ", &state);
      int count = sscanf(sid, "%li", &id);
      if (count != 1)
        return TURBINE_ERROR_COMMAND;
      printf("id: %li\n", id);
      char* name = strtok_r(NULL, " ", &state);
      puts("name");
      result = turbine_datum_file_create(id, name);
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
