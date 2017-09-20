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
    turbine::create_string  13
    turbine::create_string  14
    turbine::create_float   15

    turbine::store_integer 11 2
    turbine::store_integer 12 2
    turbine::store_string  13 "(%i,%i,%s,%0.2f)"
    turbine::store_string  14 "howdy"
    turbine::store_float   15 3.1415

    # Test without output
    turbine::printf "" [ list 13 11 12 14 15 ]
   
    # Test with output
    turbine::create_void    16
    turbine::create_string  17
    turbine::store_string 17 "Hello World"
    turbine::printf [ list 16 ] [ list 17 ]

    # Check void was set
    turbine::c::rule "17" "puts {Void was set}" type $turbine::CONTROL
}

turbine::defaults
turbine::init $servers

turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
