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

package require turbine 1.0

proc insert_last { c i d } {
    turbine::container_immediate_insert $c $i $d integer
    adlb::write_refcount_decr $c
}
proc rules { } {
    set c 1
    turbine::create_container $c integer integer

    # 12345 + 4 - 1 + 3 = 12351
    # set <container> <subscript> <member> <type>
    turbine::container_immediate_insert $c "0" 12345 integer
    turbine::container_immediate_insert $c "2" -1 integer
    
    # initialise the result
    turbine::create_integer 6

    turbine::sum_integer [ list 6 ] [ list $c ]

    # trace the result
    turbine::trace [ list ] [ list 6 ]
    
    # wait until we assign 
    turbine::c::rule "" "turbine::container_immediate_insert $c \"1\" 4 integer"
    turbine::c::rule [ list [ adlb::subscript_container $c 1 ] ] \
            "insert_last $c \"3\" 3"
}

turbine::init $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
