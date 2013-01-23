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

# Test basic readdata functionality

# SwiftScript
# string[] c;
# string s = "input.txt";
# c = readdata(s);
# foreach key in c {
#    trace(key)
#    trace(c[key])
# }

package require turbine 0.0.1

proc rules { } {

    set c 1
    turbine::create_container $c integer
    turbine::literal s string "tests/data/input.txt"

    turbine::readdata $c $s
    turbine::loop loop1_body none $c
}

proc loop1_body { parent container key } {
    turbine::trace $parent "" $key
    set t [ turbine::retrieve $key ]
    set value [ turbine::container_lookup $container $t ]
    turbine::trace $parent "" $value
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
