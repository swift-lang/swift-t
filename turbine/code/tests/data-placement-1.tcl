# Copyright 2015 University of Chicago and Argonne National Laboratory
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

# Test placement policies

package require turbine 1.0

# Should be 2 workers, 2 servers
proc main { } {
  for { set rank 0 } { $rank < 2 } { incr rank } {
    adlb::put $rank 0 worker_test 0 1
  }
}

proc worker_test { } {
  test_local
  test_local_multicreate
  test_random random
  test_random default
  test_random_multicreate random
  puts "Done: [ adlb::comm_rank ]"
}

proc test_local { } {
  set my_rank [ adlb::comm_rank ]
  set my_server [ expr {$my_rank + 2} ]
  set N 200
  for { set i 0 } { $i < $N } { incr i } {
    set id [ adlb::create $::adlb::NULL_ID integer placement local ]
    set id_server [ adlb::locate $id ]
    if { $id_server != $my_server } {
      error "Expected to allocate id on my server $my_server but was on $id_server"
    }
  }
}

proc test_local_multicreate { } {
  set my_rank [ adlb::comm_rank ]
  set my_server [ expr {$my_rank + 2} ]
  set N 200
  for { set i 0 } { $i < $N } { incr i } {
    set ids [ adlb::multicreate [ list integer placement local ] [ list integer placement local ] ]
    foreach id $ids {
      set id_server [ adlb::locate $id ]
      if { $id_server != $my_server } {
        error "Expected to allocate id on my server $my_server but was on $id_server"
      }
    }
  }
}

proc test_random { placement_mode } {
  set my_rank [ adlb::comm_rank ]
  set my_server [ expr {$my_rank + 2} ]
  set my_server_chosen 0
  set N 200
  for { set i 0 } { $i < $N } { incr i } {
    set id [ adlb::create $::adlb::NULL_ID integer placement $placement_mode ]
    set id_server [ adlb::locate $id ]
    if { $id_server == $my_server } {
      incr my_server_chosen
    }
  }

  puts "Fraction on my server: $my_server_chosen/$N for $placement_mode"
  set my_server_frac [ expr {($my_server_chosen / double($N))} ]
  if [ expr {$my_server_frac < 0.35 || $my_server_frac > 0.65} ] {
    error "Fraction allocated on my server is out of range: $my_server_frac\
           for mode $placement_mode"
  }
}

proc test_random_multicreate { placement_mode } {
  set my_rank [ adlb::comm_rank ]
  set my_server [ expr {$my_rank + 2} ]
  set my_server_chosen 0
  set N 200
  set M 3
  for { set i 0 } { $i < $N } { incr i } {
    set ids [ adlb::multicreate {*}[ lrepeat $M [ list integer placement random ] ] ]
    foreach id $ids {
      set id_server [ adlb::locate $id ]
      if { $id_server == $my_server } {
        incr my_server_chosen
      }
    }
  }

  set total [ expr $N * $M ]
  puts "Fraction on my server: $my_server_chosen/$total for $placement_mode"
  set my_server_frac [ expr {($my_server_chosen / double($total))} ]
  if [ expr {$my_server_frac < 0.35 || $my_server_frac > 0.65} ] {
    error "Fraction allocated on my server is out of range: $my_server_frac\
           for mode $placement_mode"
  }
}

turbine::defaults
turbine::init $servers
turbine::start main
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
