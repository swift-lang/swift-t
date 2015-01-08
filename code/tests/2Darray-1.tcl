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

    # By analysis, we determine that A has two insertions
    # in this scope
    puts ok0
    turbine::allocate_container A integer ref
    turbine::write_refcount_incr $A
    turbine::allocate_container t1 integer ref
    puts ok00

    turbine::literal i integer 37
    turbine::literal j integer 41
    turbine::literal k integer 59
    
    # Setup r1 and r2
    turbine::allocate r1 ref
    turbine::allocate r3 ref
    puts ok1
    turbine::c_f_create $r1 $A $i { container integer ref }
    puts ok2
    turbine::cr_f_insert $r1 $j $t1 ref
    turbine::cr_f_lookup $r1 $j $r3 ref

    # Check r3
    turbine::allocate r3_exp ref
    turbine::allocate r3_msg string
    turbine::allocate tmp_out integer
    puts ok4
    turbine::store_string $r3_msg "r3_exp"
    turbine::store_ref $r3_exp $t1
    puts ok5
    turbine::assertEqual [ list $tmp_out ] [ list $r3 $r3_exp $r3_msg ]
    
    # Setup r2
    turbine::allocate r2 ref
    puts ok6
    turbine::c_f_lookup $A $k $r2 ref
    puts ok7
    turbine::c_f_insert $A $k $t1 ref
    puts ok8

    # Check r2
    turbine::allocate r2_exp ref
    puts ok9
    turbine::allocate r2_msg string
    turbine::allocate tmp_out integer
    turbine::store_string $r2_msg "r2_exp"
    puts ok10
    turbine::store_ref $r2_exp $t1
    puts ok11
    turbine::assertEqual [ list $tmp_out ] [ list $r2 $r2_exp $r2_msg ]
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
