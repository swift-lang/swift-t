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
# Uses Swift-0 plus() function

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::integer_init 1
    turbine::integer_init 2
    turbine::integer_init 3

    turbine::store_integer 1 2
    turbine::store_integer 2 2

    set v1 [ turbine::retrieve_integer 1 ]
    set v2 [ turbine::retrieve_integer 2 ]

    turbine::rule 5 PLUS "1 2" 3 "tf: plus_integer 1 2 3"
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)

turbine::start rules

turbine::finalize

puts OK
