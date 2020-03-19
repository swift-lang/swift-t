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

# This test only flexes ADLB, it does not use Turbine features

package require turbine 1.0

enum WORK_TYPE { CMDLINE }

set servers 1
set types [ array size WORK_TYPE ]
set rc [ adlb::init $servers $types ]
assert [ expr $rc == $adlb::SUCCESS ] "Failed: adlb::init"

set amserver [ adlb::amserver ]

proc do_work {} {

    global WORK_TYPE
    while { true } {
        set work [ adlb::get $WORK_TYPE(CMDLINE) answer_rank ]
        if { [ string length $work ] } {
            puts "work: $work"
            eval exec $work
        } else {
            break
        }
    }
}

proc do_client {argc argv} {

    global WORK_TYPE
    set rank [ adlb::comm_rank ]

    if { $rank == 0 } {
        if { $argc != 1 } {
            puts "Need file argument!"
            exit 1
        }

        set batchfile [ lindex $argv 0 ]

        set fd [ open $batchfile r ]

        while { true } {
            gets $fd line
            set line [ string trimright $line ]
            if { [ string length $line ] } {
                adlb::put $adlb::RANK_ANY $WORK_TYPE(CMDLINE) $line 0 1
            }
            if { [ eof $fd ] } {
                close $fd
                break
            }
        }
    }

    do_work
}

if { $amserver == 0 } {
    puts "ADLB_SUCCESS: $adlb::SUCCESS"
    do_client $argc $argv
} else {
    adlb::server
}

adlb::finalize 1

# Help Tcl free memory
proc exit args {}
