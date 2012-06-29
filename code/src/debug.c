
#include <stdlib.h>
#include <string.h>

#include "debug.h"

bool xlb_debug_enabled = false;

void
debug_check_environment()
{
  char* v = getenv("XLB_DEBUG");
  if (v == NULL)
    return;
  if (strcmp(v, "0") == 0)
    xlb_debug_enabled = false;
  else
    xlb_debug_enabled = true;
}
