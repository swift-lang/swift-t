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

/**
   To use Python compiled packages, you may have to modify Tcl
   to use dlopen(..., RTLD_NOW | RTLD_GLOBAL)
   Cf. http://stackoverflow.com/questions/8302810/undefined-symbol-in-c-when-loading-a-python-shared-library
*/

@dispatch=WORKER
(string output) python(string code, string expr) "turbine" "0.1.0"
    [ "set <<output>> [ turbine::python 0 <<code>> <<expr>> ]" ];

@dispatch=WORKER
(string output) python_persist(string code, string expr) "turbine" "0.1.0"
    [ "set <<output>> [ turbine::python 1 <<code>> <<expr>> ]" ];
