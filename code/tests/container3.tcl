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

package require turbine 0.0.1

proc rules { } {

    set c  [ adlb::unique ]
    set f1 [ adlb::unique ]
    set f2 [ adlb::unique ]

    turbine::create_container $c integer
    turbine::create_file $f1 file1.txt
    turbine::create_file $f2 file2.txt

    # insert <container> <subscript> <member>
    turbine::container_insert $c "0" $f1
    turbine::container_insert $c "1" $f2

    set L [ adlb::enumerate $c subscripts all 0 ]
    puts "enumeration: $L"

    # This is not a real Turbine loop
    foreach subscript $L {
        set member [ turbine::container_lookup $c $subscript ]
        puts "member: $member"
        set filename [ turbine::filename $member ]
        puts "filename: $filename"
    }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
