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
    turbine::rule "worker" [ list ] $turbine::WORK $adlb::RANK_ANY "puts \"RAN RULE ON WORKER\""
    turbine::rule "after-x" [ list $x ] $turbine::WORK $adlb::RANK_ANY "puts \"RAN RULE AFTER X\""
    turbine::rule "local" [ list ] $turbine::LOCAL $adlb::RANK_ANY "puts \"RAN RULE LOCAL\""
    turbine::rule "engine" [ list ] $turbine::CONTROL $adlb::RANK_ANY "puts \"RAN RULE ON ENGINE\""
}

proc rules { } {
    turbine::allocate x integer

    turbine::rule "worker" "" $turbine::WORK $adlb::RANK_ANY "worker_fn $x"

    turbine::store_integer $x 1
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
