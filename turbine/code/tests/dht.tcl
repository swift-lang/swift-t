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

# Test ADLB store/retrieve
# Does not use Turbine features

package require turbine 1.0

namespace import turbine::string_*

turbine::init $env(ADLB_SERVERS)

if { ! [ adlb::amserver ] } {

    set count 10
    if { [ info exists env(COUNT) ] > 0 } {
        set count $env(COUNT)
    }

    set size [ adlb::comm_size ]
    set rank [ adlb::comm_rank ]
    if { $rank == 0 } {
        puts "COUNT: $count"
    }

    # puts "MPI size: $size"
    set r [ expr $rank + 1 ]
    for { set i $r } { $i <= $count } { incr i $size } {
        adlb::create $i string
        adlb::store $i string "data"
    }
} else {
    adlb::server
}

turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
