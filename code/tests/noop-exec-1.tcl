# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# Test noop executor - basic sanity test for async executors

package require turbine 0.5.0

set NOOP_WORK_TYPE 1

proc main {} {
  global NOOP_WORK_TYPE
  turbine::rule "" "DUMMY TASK" type $NOOP_WORK_TYPE
}

turbine::defaults
turbine::init $servers Turbine [ list NOOP ]

# Manually allocate rank to executor for now
if { [ adlb::rank ] == 1 } {
  turbine::c::noop_exec_register $NOOP_WORK_TYPE
  turbine::c::noop_exec_worker_loop
} else { 
 turbine::start main 
}
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
