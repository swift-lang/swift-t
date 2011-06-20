
/**
 * TCL extension for Turbine
 *
 * @author wozniak
 * */

#include <assert.h>

#include <tcl.h>

#include "src/turbine/turbine.h"

#include "src/tcl/util.h"
#include "src/tcl/turbine/tcl-turbine.h"

static int
Turbine_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  Tcl_ObjSetVar2(interp, Tcl_NewStringObj("TURBINE_SUCCESS", -1), NULL,
                 Tcl_NewIntObj(TURBINE_SUCCESS), TCL_GLOBAL_ONLY);

  Tcl_SetObjResult(interp, Tcl_NewIntObj(TURBINE_SUCCESS));

  turbine_code code = turbine_init();
  if (code != TURBINE_SUCCESS)
  {
    Tcl_AddErrorInfo(interp, " Could not initialize Turbine!\n");
    return TCL_ERROR;
  }

  return TCL_OK;
}

static int
Turbine_File_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);
  int iid;
  Tcl_GetIntFromObj(interp, objv[1], &iid);
  char* filename = Tcl_GetStringFromObj(objv[2], NULL);
  turbine_datum_id id = (turbine_datum_id) iid;
  turbine_datum_file_create(id, filename);
  return TCL_OK;
}

static int
Turbine_Rule_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(6);

  turbine_transform_id id;
  int inputs;
  turbine_datum_id input[TCL_TURBINE_MAX_INPUTS];
  int outputs;
  turbine_datum_id output[TCL_TURBINE_MAX_INPUTS];

  int code = Tcl_GetLongFromObj(interp, objv[1], &id);
  TCL_CHECK(code);

  char* name = Tcl_GetStringFromObj(objv[2], NULL);
  assert(name);

  turbine_tcl_long_array(interp, objv[3], TCL_TURBINE_MAX_INPUTS,
                         input, &inputs);
  turbine_tcl_long_array(interp, objv[4], TCL_TURBINE_MAX_OUTPUTS,
                         output, &outputs);

  char* executor = Tcl_GetStringFromObj(objv[5], NULL);
  assert(executor);

  turbine_transform transform =
  {
    .name = name,
    .executor = executor,
    .inputs = inputs,
    .input = input,
    .outputs = outputs,
    .output = output
  };

  printf("name: %s inputs: %i outputs: %i\n", name, inputs, outputs);

  turbine_rule_add(id, &transform);

  return TCL_OK;
}

static int
Turbine_Push_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  turbine_rules_push();
  return TCL_OK;
}

static int
Turbine_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  turbine_finalize();
  return TCL_OK;
}

#define ADD_COMMAND(tcl_function, c_function) \
  Tcl_CreateObjCommand(interp, tcl_function, c_function, NULL, NULL);

/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tclturbine_Init(Tcl_Interp *interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL) {
    return TCL_ERROR;
  }
  /* changed this to check for an error - GPS */
  if (Tcl_PkgProvide(interp, "ADLB", "1.0") == TCL_ERROR) {
    return TCL_ERROR;
  }
  ADD_COMMAND("turbine_init",     Turbine_Init_Cmd);
  ADD_COMMAND("turbine_file",     Turbine_File_Cmd);
  ADD_COMMAND("turbine_rule",     Turbine_Rule_Cmd);
  ADD_COMMAND("turbine_push",     Turbine_Push_Cmd);
  ADD_COMMAND("turbine_finalize", Turbine_Finalize_Cmd);
  return TCL_OK;
}
