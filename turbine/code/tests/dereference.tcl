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

# Test basic dereference functionality

# SwiftScript-ish
# int i = 3;
# int* r = &v;
# int v = *r;
# trace(*r)

package require turbine 1.0

proc rules { } {

    turbine::literal i integer 3
    turbine::literal r ref $i
    turbine::allocate v integer

    turbine::dereference_integer $v $r
    turbine::trace "" $v
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
