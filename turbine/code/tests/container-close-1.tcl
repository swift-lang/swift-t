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

# Test low-level container close functionality

# SwiftScript
# int c[];
# int i1=0, i2=1;
# int j1=98, j2=72;
# c[i1] = j1;
# c[i2] = j2;
# string s = enumerate(c)
# trace(s);

package require turbine 1.0

proc rules { } {

    turbine::allocate_container c integer string

    set i1 [ turbine::literal integer 0 ]
    set i2 [ turbine::literal integer 1 ]

    set j1 [ turbine::literal integer 98 ]
    set j2 [ turbine::literal integer 72 ]

    turbine::c_f_insert $c $i1 $j1 string
    turbine::c_f_insert $c $i2 $j2 string
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

# Help TCL free memory
proc exit args {}
