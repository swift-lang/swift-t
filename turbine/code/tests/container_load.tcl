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

# Test basic container functionality

# SwiftScript
# int c[];
# int x1 = 14;
# int x2 = 15;
# int i1;
# int i2;
# c[34] = x1;
# c[35] = x2;
# int v1 = c[i1];
# int v2 = c[i2];
# i1 = 34;
# i2 = 35;
# trace(v1);
# trace(v2);

package require turbine 1.0

proc rules { } {

    namespace import adlb::unique

    turbine::allocate_container c integer string
    turbine::allocate x1 integer
    turbine::allocate x2 integer
    turbine::allocate i1 integer
    turbine::allocate i2 integer
    turbine::allocate v1 integer
    turbine::allocate v2 integer

    turbine::store_integer $x1 14
    turbine::store_integer $x2 15

    # We pretend that we know the indices here
    # insert <container> <subscript> <type> <member>
    turbine::container_insert $c 34 $x1 string
    turbine::container_insert $c 35 $x2 string

    set L [ turbine::container_list $c ]
    puts "enumeration: $L"

    turbine::c_f_retrieve_integer $v1 $c $i1
    turbine::trace "" $v1
    turbine::c_f_retrieve_integer $v2 $c $i2
    turbine::trace "" $v2

    turbine::store_integer $i1 34
    turbine::store_integer $i2 35
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
