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

# Flex Turbine+ADLB with quick put/get
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

if { $amserver == 0 } {

    set rank [ adlb::comm_rank ]
    if { $rank == 0 } {
        puts "clock: [ clock seconds ]"
        adlb::put $adlb::RANK_ANY $WORK_TYPE(T) "hello0" 0 1
        puts "clock: [ clock seconds ]"
    }
    while 1 {
        puts "clock: [ clock seconds ]"
        set msg [ adlb::get $WORK_TYPE(T) answer_rank ]
        puts "msg: '$msg'"
        if { [ string length $msg ] == 0 } break
    }
} else {
    adlb::server
}

puts "finalizing..."
adlb::finalize 1
puts OK
