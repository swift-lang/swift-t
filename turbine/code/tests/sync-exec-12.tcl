# Copyright 2015 University of Chicago and Argonne National Laboratory
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

# Test basic sync_exec functionality


package require turbine 1.0
namespace import turbine::*

enum WORK_TYPE { T } 
adlb::init 1 1 

proc echo-app-leaf1 { } {
	turbine::c::sync_exec "" "" ""  echo Hello World
	puts "hi"
	turbine::c::log "hello!"
}

turbine::c::log "hello!"
set amserver [ adlb::amserver ]
if { $amserver == 0 } {
	set rank [ adlb::rank ]
	set tcltmp:prio [ turbine::get_priority ]
	adlb::put 3 0 "echo-app-leaf1" ${tcltmp:prio} 1

} else {
	adlb::server

}


