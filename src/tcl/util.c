
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
    assert(code == TCL_OK);
  }
  return TURBINE_SUCCESS;
}
