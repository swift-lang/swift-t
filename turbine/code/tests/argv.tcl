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

# SwiftScript (not testing data dependency)
# blob b1 = string_to_blob("hi");
# string s1 = blob_to_string(b1);
# trace(s1);

package require turbine 1.0

proc rules { } {
    # Get first flagged parameter -F=valz
    turbine::literal k1 string "F"
    turbine::create_string 20
    turbine::argv_get 20 $k1
    turbine::trace {} 20

    # Get 2nd unflagged parameter
    turbine::literal k2 integer 2
    turbine::create_string 21
    turbine::argp_get 21 $k2
    turbine::trace {} 21

    # Check non-existant parameter -G
    turbine::literal k3 string "G"
    turbine::create_integer 22
    turbine::argv_contains 22 $k3
    turbine::trace {} 22

    # Get unflagged parameter count
    turbine::create_integer 23
    turbine::argc_get 23 {}
    turbine::trace {} 23

    turbine::argv_accept {} $k1
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
