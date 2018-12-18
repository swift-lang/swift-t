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

# Test trace and basic string functionality

# SwiftScript (not testing data dependency)
# blob b1 = string_to_blob("hi");
# string s1 = blob_to_string(b1);
# trace(s1);

package require turbine 1.0

proc rules { } {

    turbine::create_blob 1

    turbine::store_blob_string 1 "hi"
    set v1 [ turbine::retrieve_blob_string 1 ]

    puts "result: $v1"
}

turbine::defaults
turbine::init $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
