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

# Basic main file for statically linked app

package require turbine 0.4
package require staticapp-1 0.1


proc rules { } {
    for { set i 0 } { $i < 10 } { incr i } {
      turbine::rule "" "helloworld $i" type $::turbine::WORK
    }
    
    for { set i 10 } { $i < 20 } { incr i } {
      turbine::rule "" "helloworld $i" type $::turbine::CONTROL
    }
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
