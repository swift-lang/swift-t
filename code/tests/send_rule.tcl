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

# Test rule on worker and control

package require turbine 0.0.1

proc worker_fn { x } {
    # Send to worker
    puts "worker_fn..."
    turbine::rule [ list ] "puts \"RAN RULE ON WORKER\"" \
         name "worker" type $turbine::WORK 
    turbine::rule [ list $x ] "puts \"RAN RULE AFTER X\"" \
         name "after-x" type $turbine::WORK 
    turbine::rule [ list ] "puts \"RAN RULE LOCAL\"" \
         name "local" type $turbine::LOCAL 
    turbine::rule [ list ] "puts \"RAN RULE ON ENGINE\"" \
         name "engine" type $turbine::CONTROL 
}

proc rules { } {
    turbine::allocate x integer

    turbine::rule {} "worker_fn $x" \
         name "worker" type $turbine::WORK

    turbine::store_integer $x 1
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
