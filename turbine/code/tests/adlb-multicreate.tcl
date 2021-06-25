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

package require turbine 1.0

turbine::defaults
turbine::init $servers

set iterations 20

if { ! [ adlb::amserver ] } {

    set rank [ adlb::comm_rank ]
    puts "rank: $rank"
    set workers [ adlb::workers ]
    if { $rank == 0 } {
	puts "iterations: $iterations"
    }
    puts "workers:    $workers"

    for { set i 1 } { $i <= $iterations } { incr i } {
        lassign [ adlb::multicreate [ list $adlb::NULL_ID integer ] \
                                    [ list $adlb::NULL_ID string ] ] \
                                    a b
        set msg "message rank:$rank:$i" 
        adlb::store $b string $msg
        set msg2 [ adlb::retrieve $b ]
        
        adlb::store $a integer $i
        set res [ adlb::retrieve $a ]
        if { $i != $res } {
          puts "Expected $i got $res"
          exit 1 
        }
    }
} else {
    adlb::server
}

turbine::finalize

puts OK
