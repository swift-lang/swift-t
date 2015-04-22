
/*
  This needs to be cleaned up. -Justin
*/

echo "pass for now"
cat << EOF > extension.c
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <tcl.h>

#include "${USER_LEAF}.h"

static int ${USER_LEAF}_extension(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj*const objv[]){
    assert(objc==$EXPECTED_ARGC);
    int rc;

    $(for i in "${USER_FUNC_ARGS[@]}"; do vartype=$(echo "${i}"|awk '{print $1}') ; varname=$(echo "${i}"|awk '{print $2}') ; echo "$vartype $varname;"  ; done)
    
    /* get values from Tcl */
    $(for i in $(seq 0 $(($EXPECTED_ARGC-2))); do vartype=$(echo "${USER_FUNC_ARGS[$i]}"|awk '{print $1}'); varname=$( echo "${USER_FUNC_ARGS[$i]}"|awk '{print $2}'); echo "rc = ($vartype*) Tcl_GetTypeFromObj(interp, objv[$i], &$varname);" ; done )
    
    /* invoke user function */
    $RET_TYPE rc2 = $USER_FUNC($(for i in "${USER_FUNC_ARGS[@]}"; do varname=$(echo "${i}" | awk '{print $2}'); echo -n '*'$varname, ;  done | sed 's/,$//'));

    // Return the exit code as the Tcl return code:
    Tcl_Obj* result = Tcl_NewLongObj(rc);
    Tcl_SetObjResult(interp, result);

    return TCL_OK;
}

int $(echo ${USER_LEAF} | sed 's/./\U&/')_Init(Tcl_Interp* interp) {
  int rc;

  Tcl_PkgProvide(interp, "leaf_main", "0.0");

  Tcl_CreateObjCommand(interp,
                       "${USER_LEAF}_extension", ${USER_LEAF}_extension,
                       NULL, NULL);
  return TCL_OK;
}
EOF
