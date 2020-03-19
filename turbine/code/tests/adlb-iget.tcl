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

# Flex Turbine+ADLB with quick put/iget
# Nice to have for quick manual experiments

# usage: mpiexec -l -n 3 bin/turbine test/adlb-putget.tcl

package require turbine 1.0

enum WORK_TYPE { T }

if [ info exists env(ADLB_SERVERS) ] {
    set servers $env(ADLB_SERVERS)
} else {
    set servers ""
}
if { [ string length $servers ] == 0 } {
    set servers 1
}
adlb::init $servers [ array size WORK_TYPE ]

set amserver [ adlb::amserver ]

set put_count 4

if { $amserver == 0 } {

    set rank [ adlb::comm_rank ]
    if { $rank == 0 } {
        puts "clock: [ clock seconds ]"
        for { set i 0 } { $i < $put_count } { incr i } {
            adlb::put $adlb::RANK_ANY $WORK_TYPE(T) "hello_$i" 0 1
        }
        puts "clock: [ clock seconds ]"
    }
    set igets_left 10
    while 1 {
        puts "clock: [ clock seconds ]"
        if { $igets_left > 0 } {
            set msg [ adlb::iget $WORK_TYPE(T) answer_rank ]
        } else {
            set msg [ adlb::get $WORK_TYPE(T) answer_rank ]
        }
        puts "msg: $msg"
        if { [ string equal $msg ""              ] } break
        if { [ string equal $msg "ADLB_SHUTDOWN" ] } break
        if { [ string equal $msg "ADLB_NOTHING" ] } {
            incr igets_left -1
        } else {
            set igets_left 10
        }

        after 100
    }
} else {
    adlb::server
}

puts "finalizing..."
adlb::finalize 1
puts OK
