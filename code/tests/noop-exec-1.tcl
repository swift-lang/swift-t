# Copyright 2014 University of Chicago and Argonne National Laboratory
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

  for { set i 0 } { $i < 100 } { incr i } {
    # A dummy task that doesn't actually add anything to executor
    turbine::rule "" {
      puts "DUMMY TASK rank: [ adlb::rank ]"
    } type $NOOP_WORK_TYPE

    # Add a task to the noop executor
    turbine::allocate x integer
    turbine::rule "" "\
      turbine::c::noop_exec_run \"NOOP TASK rank: \[ adlb::rank \]\" \
        \"turbine::store_integer $x $i\"" type $NOOP_WORK_TYPE

    turbine::rule [ list $x ] "puts \"NOOP task output set: $i\"; \
                               turbine::read_refcount_decr $x"
  }
}

set layout [ dict create servers 1 workers 2 workers_by_type \
                  [ dict create WORK 1 $turbine::NOOP_EXEC_NAME 1 ] ]
turbine::init $layout Turbine
turbine::enable_read_refcount

set noop_work_type [ turbine::adlb_work_type $turbine::NOOP_EXEC_NAME ]

turbine::c::noop_exec_register $noop_work_type
turbine::start main 
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
