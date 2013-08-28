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

package require turbine 0.0.1

namespace import turbine::string_*

turbine::defaults
turbine::init $engines $servers
turbine::enable_read_refcount

if { ! [ adlb::amserver ] } {

    set c [ adlb::unique ]
    adlb::create $c multiset integer
    
    set iterations 50
    for { set i 0 } { $i < $iterations } { incr i } {
        adlb::store $c integer [ expr 100 + $i ] 0
    }

    # Drop final slot to close multiset
    adlb::write_refcount_decr $c
    set z [ adlb::container_size $c ]

    if { $z != $iterations } {
      error "Wrong multiset size: $z expected: $iterations"
    }

    set elems [ adlb::enumerate $c members all 0 ]
    set elems_found [ dict create ]
    foreach elem $elems {
      dict set elems_found $elem 1
    }
    
    
    for { set i 0 } { $i < $iterations } { incr i } {
      set key [ expr 100 + $i ]
      if { ! [ dict exists $elems_found $key ] } {
        error "Elem $i not found"
      }
    }

    # cleanup
    adlb::read_refcount_decr $c
    puts "SUCCESS"
} else {
    adlb::server
}

turbine::finalize

puts OK
