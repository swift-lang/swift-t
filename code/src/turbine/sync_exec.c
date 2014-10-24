
/*
 * sync_exec.c
 *
 *  Created on: Oct 24, 2014
 *      Author: wozniak
 */

#include <stdio.h>
#include <stdlib.h>

#include "src/turbine/sync_exec.h"

bool
exec_system(const char* cmd, int* exitcode)
{
  printf("exec_system: %s\n", cmd);

  int rc = system(cmd);
  *exitcode = rc;

  if (rc != 0)
    return false;

  return true;
}
