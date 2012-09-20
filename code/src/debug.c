
#include <stdlib.h>
#include <string.h>

#include "debug.h"

bool xlb_debug_enabled = true;
bool xlb_trace_enabled = true;

void
debug_check_environment()
{
  char* v;

  v = getenv("ADLB_TRACE");
  if (v != NULL)
  {
    if (strcmp(v, "0") == 0)
      xlb_trace_enabled = false;
    else
      xlb_trace_enabled = true;
  }

  v = getenv("ADLB_DEBUG");
  if (v != NULL)
  {
    if (strcmp(v, "0") == 0)
    {
      xlb_debug_enabled = false;
      xlb_trace_enabled = false;
    }
    else
      xlb_debug_enabled = true;
  }
}
