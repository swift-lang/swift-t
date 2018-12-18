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
# int[] A;
# A[0] = 2;
# A[1] = 4;
# A[2] = -1;
# A[3] = 3;
# // Print out sum of array
# trace(sum(A));

package require turbine 1.0

proc rules { } {

    turbine::create_container 11 integer integer
    # adlb::write_refcount_incr 1

    turbine::create_integer 12
    turbine::store_integer 12 12345
    turbine::create_integer 13
    turbine::store_integer 13 4
    turbine::create_integer 14
    turbine::store_integer 14 -1
    turbine::create_integer 15
    turbine::store_integer 15 3

    # set <container> <subscript> <member> <type>
    turbine::container_immediate_insert 11 "0" 12345 integer
    turbine::container_immediate_insert 11 "1" 4 integer
    turbine::container_immediate_insert 11 "2" -1 integer
    turbine::container_immediate_insert 11 "3" 3 integer
    # 12345 + 4 - 1 + 3 = 12351

    # close the container
    adlb::write_refcount_decr 11

    # initialise the result
    turbine::create_integer 16

    turbine::sum_integer [ list 16 ] [ list 11 ]

    # trace the result
    turbine::trace [ list ] [ list 16 ]
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
