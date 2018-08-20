
// Copyright 2013 University of Chicago and Argonne National Laboratory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License

// LAUNCH.SWIFT

@par @dispatch=WORKER
(int status) launch(string cmd, string args[])
"turbine" "0.0" "launch_tcl";

@par @dispatch=WORKER
  (int status) launch_envs(string cmd, string args[], string envs[])
"turbine" "0.0" "launch_envs_tcl";

string EMPTY_SS[][];

@par @dispatch=WORKER
  (int status) launch_multi(int procs[],
                            string cmd[],
                            string argv[][],
                            string envs[][],
                            string color_setting="")
"turbine" "0.0" "launch_multi_tcl";

global const int EXIT_SUCCESS  = 0;
global const int EXIT_TIMEOUT  = 124; // cf. 'man timeout'
global const int EXIT_NOTFOUND = 127; // cf. 'man system(3)'
