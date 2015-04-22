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

#ifndef TCL_TURBINE_H
#define TCL_TURBINE_H

/** Maximal length of a tcl-turbine filename */
#define TCL_TURBINE_MAX_FILENAME 1024
#define TCL_TURBINE_MAX_INPUTS   128
#define TCL_TURBINE_MAX_OUTPUTS  128
#define TCL_TURBINE_READY_COUNT  1024

int DLLEXPORT
Tclturbine_Init(Tcl_Interp* interp);

#endif
