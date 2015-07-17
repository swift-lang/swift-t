# Copyright 2014 University of Chicago and Argonne National Laboratory
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

# Test global creation

package require turbine 0.7.0

turbine::defaults
turbine::init $servers


# Collective calls to create_globals
lassign [ turbine::declare_globals [ list v1 v2 v3 ] [ list integer integer integer ] ] x y z
lassign [ turbine::declare_globals [ list v4 v5 ] [ list string string ] ] a b

proc test_global { td type val} {
  set rank [ adlb::rank ]
  if { $rank == 0 } {
    adlb::store $td $type $val
  }

  # Need to make sure it was assigned
  while { ! [ adlb::exists $td ] } {
    after 10
  }

  set retrieved_val [ adlb::retrieve $td ]

  if { $val != $retrieved_val } {
    error "$td: retrieved value $retrieved_val does not match stored val $val"
  }

  puts "<$td> has expected value"
}

if { ! [ adlb::amserver ] } {
    puts "GLOBALS: [ turbine::get_globals_map ]"
    set globals [ turbine::get_globals_map ]
    if { [ dict size $globals ] != 5 } {
      error "Expected globals to have 5 entries, but had [ dict size $globals ]"
    }
    test_global [ dict get $globals v1 ] integer 1
    test_global [ dict get $globals v2 ] integer 2
    test_global [ dict get $globals v3 ] integer 3
    test_global [ dict get $globals v4 ] string hello
    test_global [ dict get $globals v5 ] string world
} else {
    adlb::server
}

turbine::finalize

puts OK
