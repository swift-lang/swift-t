
#include <stdbool.h>

#include <tcl.h>

bool list2ptrptr(Tcl_Interp* interp, Tcl_Obj* list, char*** output);
