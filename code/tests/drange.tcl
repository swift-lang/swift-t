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

# Test distributed range creation functionality

# SwiftScript
# int i = 1;
# int j = 4;
# int c[] = [i:j];
# string s = @sprintf(c)
# trace(s);

package require turbine 0.0.1

proc rules { } {

    turbine::allocate i integer
    turbine::allocate j integer
    turbine::allocate_container c integer
    turbine::allocate p integer

    global env
    if { [ info exists env(COUNT) ] } {
        set count $env(COUNT)
    } else {
        set count 100
    }
    puts "count: $count"
    turbine::store_integer $i 1
    turbine::store_integer $j $count
    turbine::store_integer $p $env(TURBINE_ENGINES)

    turbine::drange $c $i $j $p
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
