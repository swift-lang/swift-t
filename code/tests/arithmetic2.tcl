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
# x = (3+5)*(3+5);
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::allocate t3 integer
    turbine::allocate t5 integer
    turbine::allocate x integer

    turbine::store_integer $t3 3
    turbine::store_integer $t5 5

    # Use 0 as stack frame
    turbine::arithmetic 0 $x [ list "(_+_)*(_+_)" $t3 $t5 $t3 $t5 ]
    turbine::trace 0 "" $x
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
