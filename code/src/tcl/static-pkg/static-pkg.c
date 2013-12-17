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
    const char *tcl_src = turbine_lib_src[i];
    // fprintf(stderr, "Eval %s\n", turbine_lib_src_data_names[i]);
    int rc = Tcl_Eval(interp, tcl_src);
    if (rc != TCL_OK)
    {
      fprintf(stderr, "Error while loading Tcl source file (%s)\n",
                      turbine_lib_src_filenames[i]);
      return rc;
    }
  }
  return rc;
}

/*
  Register but do not initialize statically linked packages
 */
int
register_tcl_turbine_static_pkg(Tcl_Interp *interp)
{
  Tcl_StaticPackage(NULL, "turbine", Tclturbine_InitStatic,
                                       Tclturbine_InitStatic);
  int rc = Tcl_Eval(interp, "package ifneeded turbine "
              "{" TURBINE_VERSION "} {load {} turbine}");
  if (rc != TCL_OK)
  {
    Tcl_Eval(interp, "puts $errorInfo");
    return rc;
  }
  return TCL_OK;
}
