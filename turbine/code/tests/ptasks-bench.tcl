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

# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 1.0

proc rules { } {

    turbine::create_string 11

    turbine::store_string 11 "hi"

    set N 30
    for { set i 0 } { $i < $N } { incr i } {
        ptasks_1 "" [ list 11 ]
    }
}

proc ptasks_1 { result inputs } {
    turbine::rule $inputs "ptasks_1_c $result $inputs" \
        type $turbine::WORK parallelism 2
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
