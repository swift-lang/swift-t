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

# Test basic container close, enumerate functionality

# SwiftScript
# int c[];
# int i1=0, i2=1;
# c[i1] = f(i1);
# c[i2] = f(i2);
# // Stringify/concatenate keys of container c
# string s = enumerate(c);
# trace(s);
# // prints "trace: 0 1"

package require turbine 1.0

proc f { o i } {
    puts "f: $i"
    set t [ turbine::retrieve $i ]
    if { $i == 0 } {
        turbine::store_integer $o 98
    } else {
        turbine::store_integer $o 72
    }
}

proc rules { } {

    turbine::allocate_container c integer ref
    set i1 [ turbine::literal integer 0 ]
    set i2 [ turbine::literal integer 1 ]
    turbine::allocate t1 integer
    turbine::allocate t2 integer

    turbine::rule "$i1" "f $t1 $i1" type $turbine::CONTROL
    turbine::rule "$i1" "f $t2 $i2" type $turbine::CONTROL

    turbine::c_f_insert $c $i1 $t1 ref
    turbine::c_f_insert $c $i2 $t2 ref
    adlb::write_refcount_decr $c

    turbine::allocate s string
    turbine::enumerate $s $c
    turbine::trace "" $s
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
