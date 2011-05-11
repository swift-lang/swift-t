
#include "src/command.h"

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>

#include <readline/readline.h>
#include <readline/history.h>

const char* PROMPT = "turbine> ";

int
main(int argc, char* argv[])
{
  char* state;
  turbine_code code;
  code = turbine_init();

  while (true)
  {
    char* cmd = readline(PROMPT);
    if (!cmd)
      continue;
    char* copy = strdup(cmd);
    char* token = strtok_r(copy, " ", &state);
    if (strcmp(token, "quit") == 0)
      break;
    free(copy);

    turbine_code code = turbine_command(cmd);
    if (code != TURBINE_SUCCESS)
    {
      char msg[64];
      turbine_code_tostring(code, msg);
      printf("turbine: command failed: %s\n", msg);
      exit(1);
    }
  }
}
