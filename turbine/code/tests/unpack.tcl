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
# Test container unpacking

package require turbine 1.0
namespace import turbine::*

proc main { } {
  test_1D_val
  test_1D_ref
  test_1D_file_ref
  test_2D_ref
}

# Test 1D array with values stored in container
proc test_1D_val { } {
  puts "test_1D_val"
  allocate_container C integer string
  
  set x1 1234
  set x2 "word"
  set x3 3.14
  set x4 "quick brown fox"
  
  container_insert $C 4 $x2 string
  container_insert $C 1 $x1 string
  container_insert $C 8 $x3 string
  container_insert $C 12 $x4 string

  set res [ unpack_args $C 1 string ]
  puts "res: $res"
  if { [ llength $res ] != 4 } {
    error "length of res wrong"
  }
  if { [ lindex $res 0 ] != 1234 } {
    error {C[1]}
  }
  if { ! [ string equal [ lindex $res 1 ] "word" ] } {
    error {C[4]}
  }
  if { [ lindex $res 2 ] != 3.14 } {
    error {C[8]}
  }
  if { ! [ string equal [ lindex $res 3 ] "quick brown fox" ] } {
    error {C[12]}
  }
}

# Test 1D array with refs to values stored in container
proc test_1D_ref { } {
  puts "test_1D_ref"
  allocate_container C integer ref
  allocate x1 integer
  allocate x2 string
  allocate x3 float
  allocate x4 string
  container_insert $C 4 $x2 ref
  container_insert $C 1 $x1 ref
  container_insert $C 8 $x3 ref
  container_insert $C 12 $x4 ref
  store_integer $x1 1234
  store_string $x2 "word"
  store_float $x3 3.14
  store_string $x4 "quick brown fox"

  set res [ unpack_args $C 1 ref ]
  puts "res: $res"
  if { [ llength $res ] != 4 } {
    error "length of res wrong"
  }
  if { [ lindex $res 0 ] != 1234 } {
    error {C[1]}
  }
  if { ! [ string equal [ lindex $res 1 ] "word" ] } {
    error {C[4]}
  }
  if { [ lindex $res 2 ] != 3.14 } {
    error {C[8]}
  }
  if { ! [ string equal [ lindex $res 3 ] "quick brown fox" ] } {
    error {C[12]}
  }
}

proc test_1D_file_ref { } {
  puts "test_1D_file_ref"
  allocate_container C string file_ref
  allocate name1 string
  allocate name2 string
  allocate name3 string
  
  store_string $name1 "name1.sh"
  store_string $name2 "name2.txt"
  store_string $name3 "name3.swift"

  allocate_file x1 1
  set_filename_val $x1 [ retrieve $name1 ]
  allocate_file x2 1
  set_filename_val $x2 [ retrieve $name2 ]
  allocate_file x3 1
  set_filename_val $x3 [ retrieve $name1 ]
  allocate_file x4 1
  set_filename_val $x4 [ retrieve $name3 ]

  container_insert $C 1 $x1 file_ref
  container_insert $C 4 $x2 file_ref
  container_insert $C 8 $x3 file_ref
  container_insert $C 12 $x4 file_ref

  set res [ unpack_args $C 1 file_ref ]
  puts "res: $res"
  if { [ llength $res ] != 4 } {
    error "length of res wrong"
  }
  if { ! [ string equal [ lindex $res 0 ] "name1.sh" ] } {
    error {C[1]}
  }
  if { ! [ string equal [ lindex $res 1 ] "name2.txt" ] } {
    error {C[4]}
  }
  if { ! [ string equal [ lindex $res 2 ] "name1.sh" ] } {
    error {C[8]}
  }
  if { ! [ string equal [ lindex $res 3 ] "name3.swift" ] } {
    error {C[12]}
  }
}

proc test_2D_ref { } {
  puts "test_2D_ref"
  # Outer
  allocate_container C integer ref
  
  # inner container
  allocate_container C1 integer ref
  allocate_container C2 integer ref
  allocate_container C3 integer ref
  allocate_container C4 integer ref 

  container_insert $C 0 $C1 ref
  container_insert $C 1 $C2 ref
  container_insert $C 2 $C3 ref
  container_insert $C 3 $C4 ref

  allocate x integer
  store_integer $x 1
  allocate y integer
  store_integer $y 2
  allocate z integer
  store_integer $z 1234
  allocate a integer
  store_integer $a 321
  allocate b integer
  store_integer $b 3
  allocate c integer
  store_integer $c 4

  set expected [ list 1 1 1 1 2 2 2 2 1234 321 ]
  container_insert $C1 0 $x ref
  container_insert $C1 1 $x ref
  container_insert $C1 2 $x ref
  container_insert $C1 3 $x ref
  
  container_insert $C2 0 $y ref
  container_insert $C2 1 $y ref
  container_insert $C2 2 $y ref
  container_insert $C2 3 $y ref

  # Leave C3 empty
  
  container_insert $C4 0 $z ref
  container_insert $C4 1 $a ref

  set res [ unpack_args $C 2 ref ]
  puts "res: $res"

  if { [ llength $expected ] != [ llength $res ] } {
    error "Length wasn't right"
  }
  for { set i 0 } { $i < [ llength $expected ] } { incr i } {
    set act [ lindex $res $i ]
    set exp [ lindex $expected $i ]
    if { $act != $exp } {
      error "Index $i doesn't match: $act != $exp"
    }
  }
}

turbine::defaults
turbine::init $servers
turbine::start main
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
