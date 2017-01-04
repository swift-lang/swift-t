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

# Test mid-level container close functionality
# Illustrates how to get container call backs

# SwiftScript
# int c[];
# int i1=0,  i2=1,  i3=2;
# int j1=98, j2=72, j3=88;
# c[i1] = j1;
# if (i3==0) { c[i2]=j2; }
# else       { c[i3]=j3; }
# string s = enumerate(c)
# trace(s);

package require turbine 1.0

# This is like an if block
proc f { stack c i j2 j3 } {
    puts "f: $i"
    if { $i == 0 } {
        turbine::c_f_insert $c "$i" "$c $i $j2" string
    } else {
        turbine::c_f_insert $c "$i" "$c $i $j3" string
    }
    adlb::write_refcount_decr $c
}

proc rules { } {

    turbine::allocate_container c integer string

    set i1 [ turbine::literal integer 0 ]
    set i2 [ turbine::literal integer 1 ]
    set i3 [ turbine::literal integer 2 ]

    set j1 [ turbine::literal integer 98 ]
    set j2 [ turbine::literal integer 72 ]
    set j3 [ turbine::literal integer 88 ]

    turbine::c_f_insert $c $i1 "$c $i1 $j1" string

    turbine::allocate s string

    adlb::write_refcount_incr $c
    turbine::rule $i3 "f no_stack $c $i2 $j2 $j3" name RULE_F 

    adlb::write_refcount_decr $c
    turbine::enumerate $s $c
    turbine::trace "" $s
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
