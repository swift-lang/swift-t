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

# Test Coaster executor - basic sanity test
# NOTE: requires coaster service to be started

package require turbine 0.5.0

set COASTER_WORK_TYPE 1

proc coaster_task { x i secs } {
  turbine::coaster_run "sleep" [ list $secs ] \
          [ list ] [ list ] [ dict create ] \
          "coaster_task_success $x $i" "coaster_task_fail"
}

proc coaster_task_success { x i } {
  turbine::store_integer $x $i
}

proc coaster_task_fail { } {

}

proc main {} {
  global COASTER_WORK_TYPE
  
  set N 500
  set sleep 0.1

  for { set i 0 } { $i < $N } { incr i } {
    turbine::allocate x integer
    turbine::rule "" "coaster_task $x $i $sleep" type $COASTER_WORK_TYPE

    turbine::rule [ list $x ] "puts \"COASTER task output set: $i\"; \
                               turbine::read_refcount_decr $x"
  }
}

set layout [ dict create servers 1 workers 2 workers_by_type \
                  [ dict create WORK 1 $turbine::COASTER_EXEC_NAME 1 ] ]
turbine::init $layout Turbine
turbine::enable_read_refcount

set coaster_work_type [ turbine::adlb_work_type $turbine::COASTER_EXEC_NAME ]

turbine::check_can_execute $turbine::COASTER_EXEC_NAME
turbine::start main 
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
