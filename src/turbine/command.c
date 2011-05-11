
#include <stdio.h>
#include <string.h>

#include "src/turbine/turbine.h"

turbine_code turbine_command(char* cmd)
{
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
      char* name = strtok_r(cmd, " ", &state);
      puts("name");
      turbine_datum_file_create(&id, name);
      printf("id: %li\n", id);
    }
    else
      return TURBINE_ERROR_COMMAND;
  }
  else
    return TURBINE_ERROR_COMMAND;

  return TURBINE_SUCCESS;
}
