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

# Flex ADLB data store with container_insert and container_size
# No real Turbine data flow here

package require turbine 1.0

namespace import turbine::string_*

turbine::defaults
turbine::init $servers
turbine::enable_read_refcount

proc check_multiset_range { ms offset count start_val end_val } {

    set elems [ adlb::enumerate $ms members $count $offset ]

    set size_act [ llength $elems ]
    set size_exp [ expr $end_val - $start_val + 1 ]
    if { $size_act == $size_exp } {
      puts "<$ms> offset=$offset count=$count found=$size_act"
    } else {
      error "Expected $size_exp elems in <$ms> but got $size_act"
    }
    
    set enum_count [ adlb::enumerate $ms count $count $offset ]
    if { $enum_count != $size_act } {
      error "adlb::enumerate with count gave incorrect result: $enum_count"
    }


    set elems_found [ dict create ]
    foreach elem $elems {
      dict set elems_found $elem 1
    }
    
    for { set val $start_val } { $val <= $end_val } { incr val } {
      puts "CHECK <$ms> contains $val"
      if { ! [ dict exists $elems_found $val ] } {
        error "Elem $val not found"
      }
    }
}

if { ! [ adlb::amserver ] } {

    set c [ adlb::unique ]
    adlb::create $c multiset integer
    
    set iterations 50
    for { set i 0 } { $i < $iterations } { incr i } {
        adlb::insert $c "" [ expr 100 + $i ] integer 0
    }

    # Drop final slot to close multiset
    adlb::write_refcount_decr $c
    set z [ adlb::container_size $c ]

    if { $z != $iterations } {
      error "Wrong multiset size: $z expected: $iterations"
    }

    # Request all
    check_multiset_range $c 0 all 100 [ expr 100 + $iterations - 1 ]
    
    # Request > number in set
    check_multiset_range $c 0 5000 100 [ expr 100 + $iterations - 1 ]
    
    # Request == number in set
    check_multiset_range $c 0 $iterations 100 [ expr 100 + $iterations - 1 ]

    # Request < number in set
    check_multiset_range $c 0 [ expr $iterations - 10 ] \
                         100 [ expr 100 + $iterations - 1 - 10 ]

    # Request all starting at offset
    check_multiset_range $c 2 all 102 [ expr 100 + $iterations - 1 ]

    # Request none starting at offset
    check_multiset_range $c 50 all 0 -1
    
    # Request several starting at offset
    check_multiset_range $c 25 5 125 129

    
    set failed [ catch {adlb::enumerate $c subscripts all 0} ]
    if { ! $failed } {
      error "Expected adlb::enumerate with subscripts on multiset to fail"
    }
    
    set failed [ catch {adlb::enumerate $c dict all 0} ]
    if { ! $failed } {
      error "Expected adlb::enumerate with subscripts on multiset to fail"
    }


    # cleanup
    adlb::read_refcount_decr $c
    puts "SUCCESS"
} else {
    adlb::server
}

turbine::finalize

puts OK
