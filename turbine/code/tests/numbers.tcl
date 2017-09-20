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

# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 1.0

proc rules { } {

    turbine::create_integer 11
    turbine::create_integer 12
    turbine::create_integer 13

    turbine::store_integer 11 2
    turbine::store_integer 12 2

    set v1 [ turbine::retrieve_integer 11 ]
    set v2 [ turbine::retrieve_integer 12 ]
    set v3 [ expr $v1 + $v2 ]

    turbine::store_integer 13 $v3
    puts "result: $v3"
    set t [ adlb::typeof 13 ]
    puts "type: $t"
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
