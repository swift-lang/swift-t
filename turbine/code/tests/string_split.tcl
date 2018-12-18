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

# Test string split function

# Requires TURBINE_LOG=1

package require turbine 1.0

proc rules { } {

    turbine::create_string 11
    turbine::create_string 12
    turbine::create_string 13

    turbine::store_string 11 "hi how are you"

    turbine::create_container 18 integer string
    turbine::split 18 11

    turbine::create_container 19 integer string
    turbine::store_string 12 "/bin:/usr/evil name/p:/usr/bin"
    turbine::store_string 13 ":"
    turbine::split 19 { 12 13 }

    turbine::rule 19 "check 19"
}

proc check { container } {
    set s1 [ turbine::container_lookup $container 1 ]
    puts "s1: $s1"
    set s2 [ turbine::container_lookup $container 2 ]
    puts "s2: $s2"
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
