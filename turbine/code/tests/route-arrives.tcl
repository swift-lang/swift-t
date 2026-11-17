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

# Test rules with optional subscript inputs (rule option "optional"):
# a rule runs when its container key is inserted, or when the
# container closes without it.

package require turbine 1.0

proc wait_key { c k } {
    turbine::c::rule [ list [ adlb::subscript_container $c $k ] ] \
        [ list check $c $k ] optional 1
}

proc check { c k } {
    puts "route-check: '$k' [ adlb::exists_sub $c $k ]"
}

proc insert_late { c } {
    turbine::container_immediate_insert $c "k 2" "v2" string
    adlb::write_refcount_decr $c
    # Container is now closed: this must run at once
    wait_key $c "yy"
}

proc rules { } {
    turbine::allocate_container c string string

    # Inserted before the rule
    turbine::container_immediate_insert $c "k1" "v1" string
    wait_key $c "k1"
    # Inserted later; the key contains a space
    wait_key $c "k 2"
    # Never inserted
    wait_key $c "zz"

    turbine::c::rule "" [ list insert_late $c ]
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
