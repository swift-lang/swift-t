
#include <assert.h>

#include "src/tcl/util.h"

/**
   Obtain array of long integers from TCL list
   @param interp The TCL interpreter
   @param list The TCL list
   @param max The maximal output size
   @param output Where to write the output
   @param count The actual output size
*/
turbine_code
turbine_tcl_long_array(Tcl_Interp* interp, Tcl_Obj* list, int max,
                      long* output, int* count)
{
  Tcl_Obj** entry;
  int code = Tcl_ListObjGetElements(interp, list, count, &entry);
  assert(code == TCL_OK);
  assert(*count < max);
  for (int i = 0; i < *count; i++)
  {
    code = Tcl_GetLongFromObj(interp, entry[i], &output[i]);
    if (code != TCL_OK)
      return TURBINE_ERROR_NUMBER_FORMAT;
  }
  return TURBINE_SUCCESS;
}

turbine_code
turbine_tcl_string_array(Tcl_Interp* interp, Tcl_Obj* list, int max,
                         char** output, int* count)
{
  Tcl_Obj** entry;
  int code = Tcl_ListObjGetElements(interp, list, count, &entry);
  assert(code == TCL_OK);
  assert(*count < max);
  for (int i = 0; i < *count; i++)
  {
    char* t = Tcl_GetStringFromObj(entry[i], NULL);
    if (code != TCL_OK)
          return TURBINE_ERROR_UNKNOWN;
    output[i] = t;
  }
  return TURBINE_SUCCESS;
}

#define TCL_CONDITION_MSG_MAX 1024

void tcl_condition_failed(Tcl_Interp* interp, Tcl_Obj* command,
                          const char* format, ...)
{
  va_list va;
  va_start(va,format);
  char buffer[TCL_CONDITION_MSG_MAX];
  char* commandname = Tcl_GetStringFromObj(command, NULL);
  char* p = &buffer[0];
  p += sprintf(p, "\n");
  p += sprintf(p, "error: ");
  p += sprintf(p, "%s: ", commandname);
  p += vsprintf(p, format, va);
  p += sprintf(p, "\n\n");
  va_end(va);
  // This error will usually be caught and printed again, but
  // we print it here just to be sure it gets printed at least once
  printf("%s", buffer);
  fflush(stdout);
  Tcl_AddErrorInfo(interp, buffer);
}
