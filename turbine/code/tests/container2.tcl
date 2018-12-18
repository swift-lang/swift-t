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
# file[] f1;
# file f2<"file1.txt">;
# file f3<"file2.txt">;
# f1[0] = f2;
# f2[1] = f3;
# // Print out contents of f1

package require turbine 1.0

proc rules { } {

    turbine::create_container 1 integer string
    turbine::create_string 2
    turbine::create_string 3

    turbine::store_string 2 "string2"
    turbine::store_string 3 "string3"

    # set <container> <subscript> <member>
    turbine::container_insert 1 "0" 2 string
    turbine::container_insert 1 "1" 3 string

    set L [ adlb::enumerate 1 subscripts all 0 ]
    # sort to get in non-implementation-dependent order
    set L [ lsort -integer $L ]
    puts "enumeration: $L"

    # This is not a real Turbine loop
    foreach subscript $L {
        puts "subscript: $subscript"
        set member [ turbine::container_lookup 1 $subscript ]
        puts "member: $member"
        set s [ turbine::retrieve_string $member ]
        puts "string: $s"
    }
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
