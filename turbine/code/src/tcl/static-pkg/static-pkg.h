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

#ifndef __TURBINE_STATIC_PACKAGE_H__
#define __TURBINE_STATIC_PACKAGE_H__

#include <tcl.h>

/*
  Register a package for later loading by package require.  Upon the first
  package require, the package will be loaded by calling the init function.
  If there is an error in initialising the package, we will print details
  here, then return TCL_ERROR.
 */
int register_static_pkg(Tcl_Interp *interp, const char *package,
                        const char *version, Tcl_PackageInitProc *init);

int register_tcl_turbine_static_pkg(Tcl_Interp *interp);

/*
  Evaluate a script string converted from a file.
  This function adds some error messages, and ensure bytecode compilation
  can happen.
  script: tcl source string to eval
  script_bytes: length of script, or -1 if unknown
  srcfile: file that it was derived from, for error messages

  On error, prints error information and returns TCL_ERROR
 */
int tcl_eval_bundled_file(Tcl_Interp *interp, const char *script,
                          int script_bytes, const char *srcfile);

#endif //__TURBINE_STATIC_PACKAGE_H__
