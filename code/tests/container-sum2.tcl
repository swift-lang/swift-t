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
# this is same as previous test but we force different order

package require turbine 0.0.1

proc insert_last { c i d } {
    turbine::container_immediate_insert $c $i $d integer
    adlb::write_refcount_decr $c
}
proc rules { } {
    set c 1
    turbine::create_container $c integer integer

    turbine::create_integer 2
    turbine::store_integer 2 12345
    turbine::create_integer 3
    turbine::create_integer 4 
    turbine::store_integer 4 -1
    turbine::create_integer 5 
    # 12345 + 4 - 1 + 3 = 12351

    # set <container> <subscript> <member> <type>
    turbine::container_immediate_insert $c "0" 2 integer
    turbine::container_immediate_insert $c "2" 4 integer
    turbine::container_immediate_insert $c "3" 5 integer
    
    # initialise the result
    turbine::create_integer 6

    turbine::sum_integer [ list 6 ] [ list $c ]

    # trace the result
    turbine::trace [ list ] [ list 6 ]
    
    # only finalise the container later make sure
    # sum handles non-finished array correctly
    turbine::c::rule "3 5" "insert_last $c 1 3"
    
    # wait until we assign 
    turbine::c::rule "" "turbine::store_integer 3 4"
    turbine::c::rule "" "turbine::store_integer 5 3"
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
