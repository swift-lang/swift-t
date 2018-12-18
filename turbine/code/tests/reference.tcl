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

# Test basic container reference functionality

# SwiftScript-ish
# int i = 37;
# int j = 41;
# int c[];
# // reference before insertion
# int* r1 = &c[i];
# int v1 = *r1;
# trace(v1);
# c[i] = j;
# // prints "trace: 41"

# // reference after insertion
# int k = 72;
# c[j] = k;
# int* r2 = &c[j]
# int v2 = *r2;
# trace(v2);
# // prints "trace: 72"

package require turbine 1.0

proc rules { } {

    turbine::allocate_container c integer ref
    # Need to increment read reference count since read twice
    turbine::read_refcount_incr $c 1

    set i [ turbine::literal integer 37 ]
    set j [ turbine::literal integer 41 ]
    turbine::allocate r1 ref
    turbine::allocate v1 integer
    turbine::c_f_lookup $c $i $r1 ref
    turbine::dereference_integer $v1 $r1
    turbine::trace "" $v1
    turbine::c_f_insert $c $i $j ref

    set k [ turbine::literal integer 72 ]
    turbine::c_f_insert $c $j $k ref
    turbine::allocate r2 ref
    turbine::allocate v2 integer
    turbine::c_f_lookup $c $j $r2 ref
    turbine::dereference_integer $v2 $r2
    turbine::trace "" $v2
    puts "Executed rules"
}

turbine::defaults
turbine::init $servers
turbine::enable_read_refcount
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
