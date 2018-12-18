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

# Test nested container operation

# SwiftScript-ish
# int A[][];
# int i1 = 31;
# int j1 = 41;
# int k1 = 51;
# A[i1][j1] = k1;
# int i2 = 32;
# int j2 = 42;
# int k2 = 52;
# A[i2][j2] = k2;
# int i3 = 32; # Note i3 == i2
# int j3 = 43;
# int k3 = 53;
# A[i3][j3] = k3;

# This could be implemented without references but is
# done with references here for testing

package require turbine 1.0

proc rules { } {

    turbine::allocate_container A integer ref

    turbine::literal i1 integer 31
    turbine::literal j1 integer 41
    turbine::literal k1 integer 51


    turbine::allocate r1 ref
    adlb::write_refcount_incr $A 2
    turbine::c_f_create $r1 $A $i1 [ list container integer string ]
    turbine::cr_f_insert $r1 $j1 $k1 string

    turbine::literal i2 integer 32
    turbine::literal j2 integer 42
    turbine::literal k2 integer 52

    turbine::allocate r2 ref
    adlb::write_refcount_incr $A 2
    turbine::c_f_create $r2 $A $i2 [ list container integer string ]
    turbine::cr_f_insert $r2 $j2 $k2 string

    turbine::literal i3 integer 31
    turbine::literal j3 integer 43
    turbine::literal k3 integer 53

    turbine::allocate r3 ref
    adlb::write_refcount_incr $A 2
    turbine::c_f_create $r3 $A $i3 [ list container integer string ]
    turbine::cr_f_insert $r3 $j3 $k3 string
    adlb::write_refcount_decr $A 1
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
