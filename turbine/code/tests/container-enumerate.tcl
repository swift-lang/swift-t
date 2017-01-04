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
# file[] c;
# file f1<"file1.txt">;
# file f2<"file2.txt">;
# c[0] = f1;
# c[1] = f2;
# // Print out contents of c

package require turbine 1.0

proc rules { } {

    set c  [ adlb::unique ]
    set s1 [ adlb::unique ]
    set s2 [ adlb::unique ]

    turbine::create_container $c integer string
    turbine::create_string $s1 0
    turbine::store_string $s1 "hello"
    turbine::create_string $s2 0
    turbine::store_string $s2 "howdy"

    # insert <container> <subscript> <type> <member>
    turbine::container_insert $c "0" $s1 string
    turbine::container_insert $c "1" $s2 string

    # Output member TDs for check script:
    puts "member1: $s1"
    puts "member2: $s2"

    set L1 [ adlb::enumerate $c subscripts 2 0 ]
    puts "subscripts: [ lsort -integer $L1 ]"

    # Check which order they appear in
    set swap [ expr [ lindex $L1 0 ] == 1 ]

    set L2 [ adlb::enumerate $c members 2 0 ]
    if { $swap } {
      puts "members: [ lreverse $L2 ]"
    } else {
      puts "members: $L2"
    }

    set L3 [ adlb::enumerate $c dict 2 0 ]
    set sorted_L3 [ dict create ]
    foreach k [ lsort [ dict keys $L3 ] ] {
      dict append sorted_L3 $k [ dict get $L3 $k ]
    }
    puts "dict: $sorted_L3"

    set n [ adlb::enumerate $c count all 0 ]
    puts "count: $n"
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
