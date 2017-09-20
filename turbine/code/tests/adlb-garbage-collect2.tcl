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
# Test garbage collection

package require turbine 1.0


turbine::defaults
turbine::init $servers
turbine::enable_read_refcount

proc test_insert_then_decr_ref {} {
  puts test_insert_then_decr_ref
  set C [ adlb::unique ]
  adlb::create $C container integer integer

  set i1 [ adlb::unique ]
  adlb::create $i1 integer
  adlb::store $i1 integer 0
  adlb::insert $C 0 $i1 integer

  set i2 [ adlb::unique ]
  adlb::create $i2 integer
  adlb::store $i2 integer 0
  # Insert and close
  adlb::insert $C 1 $i2 integer 1

  set res [ adlb::lookup $C 0 ]
  if { $res != $i1 } {
    puts "res: $res != $i1"
    exit 1
  }

  if { ! [ adlb::exists $C ] } {
    puts "<$C> should exist"
    exit 1
  }

  adlb::refcount_incr $C r -1
  
  if { [ adlb::exists $C ] } {
    puts "<$C> should be destroyed"
    exit 1
  }
  # TODO: check that members destroyed
}

proc test_decr_ref_then_insert {} {
  puts test_decr_ref_then_insert 
  set C [ adlb::unique ]
  adlb::create $C container integer integer

  set i1 [ adlb::unique ]
  adlb::create $i1 integer
  adlb::store $i1 integer 0
  adlb::insert $C 0 $i1 integer

  # Take read refcount to 0
  adlb::refcount_incr $C r -1

  # Should still be able to insert since container write ref still > 0 
  set i2 [ adlb::unique ]
  adlb::create $i2 integer
  adlb::store $i2 integer 0
  # Insert 
  adlb::insert $C 1 $i2 integer 1
  
  if { [ adlb::exists $C ] } {
    puts "<$C> should be destroyed"
    exit 1
  }
  # TODO: check that members destroyed
}

if { ! [ adlb::amserver ] } {
  test_insert_then_decr_ref
  test_decr_ref_then_insert
} else {
  adlb::server
}

turbine::finalize

puts OK
