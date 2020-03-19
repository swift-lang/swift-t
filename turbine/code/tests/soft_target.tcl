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

# Test soft targeted rule on two ranks

package require turbine 1.0
namespace import turbine::*

proc task_fixed { msg tag rank sleep_ms } {
    if { $rank != [ adlb::comm_rank ] } {
      error "Rank on wrong rank: $msg expected rank: $rank\
             actual rank [ adlb::comm_rank ]"
    }
    puts "[ adlb::comm_rank ]: TASK: FIXED OK: $tag"
    after $sleep_ms
}

proc task_flexible { sleep_ms tag } {
    puts "[ adlb::comm_rank ]: TASK: FLEXIBLE $tag"
    after $sleep_ms
}

proc rules { } {
    # Give time for workers to start up
    after 500
    # First check that rules go to target when there is limited work
    for { set rank 1 } { $rank <= 2 } { incr rank } {
      turbine::rule {} [ list task_fixed tag:$rank "part1" $rank 100 ] \
            type $turbine::WORK target $rank strictness SOFT
    }

    # Give time for previous work to start running
    after 100

    # Second check that rules go to multiple ranks when lots of work
    for { set i 0 } { $i <= 3 } { incr i } {
      turbine::rule {}                      \
          [ list task_flexible 200 tag:$i ] \
          target 1 strictness SOFT
    }
    # Drop into main worker loop, so some tasks should run on this rank
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
