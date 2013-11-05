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

# Test container serialization

package require turbine 0.0.1

proc test_retrieve { } {
    # Check looking up

    set c1 [ adlb::create $::adlb::NULL_ID container integer string ]
    adlb::insert $c1 1 "val1" string
    adlb::insert $c1 2 "val2" string
    adlb::insert $c1 3 "val3" string
    
    set c1_check [ adlb::retrieve $c1 ]
    puts "c1_check: $c1_check"
    #TODO: check it's a 3 element dict
}

proc test_store { } {
    # Check we can store dict to ADLB container
    set c2_val [ dict create 1 val1 2 val2 3 val3 ]
    set c2 [ adlb::create $::adlb::NULL_ID container integer string ]
    adlb::store $c2 container $c2_val
    #TODO: extra type info adlb::store $c2 container integer string $c2_val

    set c2_size [ adlb::container_size $c2 ]

    set c2_0 [ adlb::lookup $c2 0 ]
    set c2_1 [ adlb::lookup $c2 1 ]
    set c2_2 [ adlb::lookup $c2 2 ]

    #TODO: check c2 parameters
    puts "c2_size: $c2_size elems: \[ $c2_0 $c2_1 $c2_2 \]"

}

proc rules { } {
   test_retrieve
   # TODO: store doesn't work yet
   #test_store
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
