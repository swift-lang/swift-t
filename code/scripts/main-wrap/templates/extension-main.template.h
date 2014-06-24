
/*
changecom(`dnl')
# Define convenience macros
define(`getenv', `esyscmd(printf -- "$`$1' ")')
define(`getenv_nospace', `esyscmd(printf -- "$`$1'")')
*/

#include <tcl.h>

int getenv_nospace(USER_LEAF_INIT)(Tcl_Interp* interp);
