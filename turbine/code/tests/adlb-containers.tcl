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

# Flex ADLB data store with container_insert and container_size
# No real Turbine data flow here

package require turbine 1.0

namespace import turbine::string_*

turbine::defaults
turbine::init $servers

if { ! [ adlb::amserver ] } {

    set c [ adlb::unique ]
    adlb::create $c container integer string

    set iterations [ expr 5 + $c ]
    for { set i [ expr $c + 1 ] } { $i < $iterations } { incr i } {
        set s [ adlb::unique ]
        adlb::create $s string
        adlb::store $s string "message $i"
        adlb::insert $c $i $s string
    }

    adlb::insert $c string-test "string value" string 

    # Drop final slot to close array
    adlb::write_refcount_decr $c
    set z [ adlb::container_size $c ]
    puts "container size: $z"
} else {
    adlb::server
}

turbine::finalize

puts OK
