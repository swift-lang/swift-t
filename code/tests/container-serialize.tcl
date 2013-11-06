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

proc test_retrieve_container { } {
    # Check looking up

    set c1 [ adlb::create $::adlb::NULL_ID container integer string ]
    adlb::insert $c1 1 "val1" string
    adlb::insert $c1 2 "val2" string
    adlb::insert $c1 3 "val3" string
    
    set c1_check [ adlb::retrieve $c1 ]
    puts "c1_check: $c1_check"

    if { [ dict size $c1_check ] != 3 } {
        error "c1 entries [ dict size $c1_check ] expected 3"
    }
    
    set c1_1 [ dict get $c1_check 1 ]
    set c1_2 [ dict get $c1_check 2 ]
    set c1_3 [ dict get $c1_check 3 ]

    if { $c1_1 != "val1" } {
        error "c1\[1\] $c1_1"
    }
    if { $c1_2 != "val2" } {
        error "c1\[2\] $c1_2"
    }
    if { $c1_3 != "val3" } {
        error "c1\[3\] $c1_3"
    }
}

proc test_retrieve_multiset { } {
    # Check looking up

    set m1 [ adlb::create $::adlb::NULL_ID multiset string ]
    adlb::store $m1 string "val1" 0
    adlb::store $m1 string "val2" 0
    adlb::store $m1 string "val3" 
    
    set m1_check [ adlb::retrieve $m1 ]
    puts "m1_check: $m1_check"
    
    if { [ llength $m1_check ] != 3 } {
        error "m1 entries [ llength $m1_check ] expected 3"
    }
    
    set m1_check [ lsort $m1_check ]

    set m1_1 [ lindex $m1_check 0 ]
    set m1_2 [ lindex $m1_check 1 ]
    set m1_3 [ lindex $m1_check 2 ]

    if { $m1_1 != "val1" } {
        error "m1\[1\] $c1_1"
    }
    if { $m1_2 != "val2" } {
        error "m1\[2\] $c1_2"
    }
    if { $m1_3 != "val3" } {
        error "m1\[3\] $c1_3"
    }
}

proc test_store_container { } {
    # Check we can store dict to ADLB container
    set c2_val [ dict create 1 val1 2 val2 3 val3 ]
    set c2 [ adlb::create $::adlb::NULL_ID container integer string ]
    adlb::store $c2 container integer string $c2_val

    set c2_size [ adlb::container_size $c2 ]

    set c2_1 [ adlb::lookup $c2 1 ]
    set c2_2 [ adlb::lookup $c2 2 ]
    set c2_3 [ adlb::lookup $c2 3 ]

    puts "c2_size: $c2_size elems: \[ $c2_1 $c2_2 $c2_3 \]"

    if { $c2_size != 3 } {
        error "c2 entries $c2_size expected 3"
    }
    if { $c2_1 != "val1" } {
        error "c2\[1\] $c2_1"
    }
    if { $c2_2 != "val2" } {
        error "c2\[2\] $c2_2"
    }
    if { $c2_3 != "val3" } {
        error "c2\[3\] $c2_3"
    }
}

proc test_store_multiset { } {
    # Check we can store list to ADLB multiset
    set m2_val [ list val1 val2 val3 ]
    set m2 [ adlb::create $::adlb::NULL_ID multiset string ]

    #TODO: need to store rather than append
    adlb::store $m2 multiset string $m2_val

    set m2_size [ adlb::container_size $m2 ]

    set m2_check [ lsort [ adlb::enumerate $m2 members all 0 ] [
    set m2_0 [ lindex $m2_check 0 ]
    set m2_1 [ lindex $m2_check 1 ]
    set m2_2 [ lindex $m2_check 2 ]

    puts "m2_size: $m2_size elems: \[ $m2_0 $m2_1 $m2_2 \]"

    if { $m2_size != 3 } {
        error "m2 entries $m2_size expected 3"
    }
    if { $m2_0 != "val1" } {
        error "m2\[0\] $m2_0"
    }
    if { $m2_1 != "val2" } {
        error "m2\[1\] $m2_1"
    }
    if { $m2_2 != "val3" } {
        error "m2\[2\] $m2_2"
    }
}

proc rules { } {
   test_retrieve_container
   test_retrieve_multiset
   test_store_container
   # TODO: doesn't pass yet
   #test_store_multiset
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
