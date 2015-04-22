/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */


#include "static-pkg.h"
#include "src/tcl/static-pkg/tcl-turbine-src.h"
#include "src/tcl/turbine/tcl-turbine.h"
#include "src/turbine/turbine-version.h"

// Wrapper to initialize C module and tcl source files
static int
Tclturbine_InitStatic(Tcl_Interp *interp)
{
  //fprintf(stderr, "Callback to init static package tclturbine\n");
  int rc = Tclturbine_Init(interp);
  //fprintf(stderr, "Inited static package tclturbine\n");
  if (rc != TCL_OK)
  {
    fprintf(stderr, "Error initializing Tcl Turbine C package\n");
    return rc;
  }

  // Initialize list of data
  turbine_lib_src_init();
  
  for (int i = 0; i < turbine_lib_src_len; i++)
  {
    // These are null terminated strings so we can use directly
    int rc = tcl_eval_bundled_file(interp, (const char *)turbine_lib_src[i],
                                   (int)turbine_lib_src_lens[i],
                                   turbine_lib_src_filenames[i]);
    if (rc != TCL_OK)
    {
      return TCL_ERROR;
    }
  }
  return rc;
}

int register_static_pkg(Tcl_Interp *interp, const char *package,
                        const char *version, Tcl_PackageInitProc *init)
{
  // Use same name for "load" command
  const char *load_pkg = package;
  Tcl_StaticPackage(NULL, load_pkg, init, init);

  Tcl_Obj *script = Tcl_ObjPrintf(
        "package ifneeded {%s} {%s} { load {} {%s} }",
        package, version, load_pkg);
  Tcl_IncrRefCount(script);
  int rc = Tcl_EvalObjEx(interp, script, 0);
  Tcl_DecrRefCount(script);
  if (rc != TCL_OK)
  {
    fprintf(stderr, "Error initializing Tcl package %s %s", package,
                    version);
    Tcl_Eval(interp, "puts $errorInfo");
    return rc;
  }
  return TCL_OK;
}

/*
  Register but do not initialize statically linked packages
 */
int
register_tcl_turbine_static_pkg(Tcl_Interp *interp)
{
  return register_static_pkg(interp, "turbine", TURBINE_VERSION,
                             Tclturbine_InitStatic);
}


int tcl_eval_bundled_file(Tcl_Interp *interp, const char *script,
                          int script_bytes, const char *srcfile)
{
  int rc;
  rc = Tcl_EvalEx(interp, script, script_bytes, 0);

  if (rc != TCL_OK) {
    fprintf(stderr, "Error while loading Tcl code originally from file %s:\n",
                     srcfile);
    Tcl_Eval(interp, "puts $::errorInfo");
    return TCL_ERROR;
  }
  return TCL_OK;
}
