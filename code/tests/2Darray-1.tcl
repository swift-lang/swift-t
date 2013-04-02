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
# int i = 37;
# int j = 41;
# int k = 59;
# A[i][j] = k;

# This could be implemented without references but is
# done with references here for testing

package require turbine 0.0.1

proc rules { } {

    # By analysis, we determine that A has one insertion
    # in this scope: an anonymous container t1
    # By analysis, we determine that t1 has one insertion in this
    # scope
    turbine::allocate_container A integer
    turbine::allocate_container t1 integer

    turbine::literal i integer 37
    turbine::literal j integer 41
    turbine::literal k integer 59

    turbine::allocate r1 integer
    turbine::c_f_lookup $A $i $r1 integer
    turbine::c_f_insert $A $i $t1
    turbine::cr_f_insert $r1 $j $t1 $A
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
