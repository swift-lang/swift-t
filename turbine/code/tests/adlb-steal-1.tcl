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

# Flex Turbine+ADLB for steals

# usage: bin/turbine -l -n 6 tests/adlb-steal-1.tcl

package require turbine 1.0

enum WORK_TYPE { T }

set start [ clock seconds ]

proc clock_report { } {
    global start
    set t [ clock seconds ]
    set d [ expr $t - $start ]
    puts "clock: $d"
}

if [ info exists env(ADLB_SERVERS) ] {
    set servers $env(ADLB_SERVERS)
} else {
    set servers 2
}

adlb::init $servers [ array size WORK_TYPE ]

set amserver [ adlb::amserver ]

set PUTS 10

if { $amserver == 0 } {

    set rank [ adlb::comm_rank ]
    if { $rank == 0 } {
        for { set i 0 } { $i < $PUTS } { incr i } {
            adlb::put $adlb::RANK_ANY $WORK_TYPE(T) "wu-$i" 0
        }
    } else {
        after 5000
        while 1 {
            set msg [ adlb::get $WORK_TYPE(T) answer_rank ]
            puts "msg: '$msg'"
            if { [ string length $msg ] == 0 } break
            # puts "answer_rank: $answer_rank"
        }
    }
    puts "WORKER_DONE"
} else {
    adlb::server
}

adlb::finalize 1
puts OK

proc exit args {}
