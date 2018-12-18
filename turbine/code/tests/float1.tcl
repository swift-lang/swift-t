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

package require turbine 1.0

proc rules { } {

    namespace import adlb::unique
    namespace import turbine::float_*
    namespace import turbine::arithmetic

    turbine::allocate t1 float
    turbine::allocate t2 float
    turbine::allocate x float

    turbine::store_float $t1 3
    turbine::store_float $t2 5

    # Use 0 as stack frame
    turbine::plus_float [ list $x ] [ list $t1 $t2 ]
    turbine::trace "" $x
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK
