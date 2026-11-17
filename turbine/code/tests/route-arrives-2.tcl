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

# Test turbine::route_arrives, which the route statement uses:
# result is 1 when the key is inserted, 0 if the container closes
# without it.

# SwiftScript
# string c[string];
# c["a"] = "va";
# boolean ra = route_arrives(c, "a");
# boolean rb = route_arrives(c, "b");
# boolean rz = route_arrives(c, "zz");
# c["b"] = "vb";  // later, then c closes

package require turbine 1.0

proc report { k r } {
    puts "route_arrives: $k [ turbine::retrieve_decr_integer $r ]"
}

proc insert_late { c } {
    turbine::container_immediate_insert $c "b" "vb" string
    adlb::write_refcount_decr $c
}

proc rules { } {
    turbine::allocate_container c string string
    turbine::container_immediate_insert $c "a" "va" string

    set keys [ list a b zz ]
    # route_arrives consumes one read reference on c per call
    adlb::read_refcount_incr $c [ llength $keys ]
    foreach k $keys {
        set key [ turbine::literal string $k ]
        set r [ turbine::allocate integer ]
        turbine::route_arrives $r [ list $c $key ]
        turbine::c::rule $r [ list report $k $r ]
    }

    turbine::c::rule "" [ list insert_late $c ]
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
