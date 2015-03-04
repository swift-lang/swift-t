
/*
 * sync_exec.c
 *
 *  Created on: Oct 24, 2014
 *      Author: wozniak
 */

#include <stdio.h>
#include <stdlib.h>
#include <sys/wait.h>

#include "src/turbine/sync_exec.h"

bool
exec_system(const char* cmd, int* exitcode)
{
  // printf("exec_system: %s\n", cmd);

  int status = system(cmd);
  *exitcode = WEXITSTATUS(status);

  if (status != 0)
  {
    // printf("Command exited with code: %i\n", *exitcode);
    // printf("Command was: %s\n", cmd);
    return false;
  }

  return true;
}
