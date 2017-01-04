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

# Test what happens if we try to lookup something that does not exist

package require turbine 1.0
adlb::init 1 1

if [ adlb::amserver ] {
    adlb::server
} else {
    adlb::create 3 container integer string
    set result [ adlb::lookup 3 2 ]
    # Should not get here- Tcl error not handled
    puts "lookup: $result"
}

adlb::finalize 1
puts OK
