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
# Test garbage collection

package require turbine 1.0


turbine::defaults
turbine::init $servers
turbine::enable_read_refcount

if { ! [ adlb::amserver ] } {
  set x [ adlb::unique ]
  adlb::create $x integer
  adlb::store $x integer 0
  if { ! [ adlb::exists $x ] } {
    puts "x does not exist after store"
    exit 1
  }

  # Set reference count to 0 to trigger destruction
  adlb::refcount_incr $x r -1
  if { [ adlb::exists $x ] } {
    puts "x exists after refcount 0"
    exit 1
  }

  set y [ adlb::unique ]
  adlb::create $y integer
  adlb::refcount_incr $y r -1
  # Set reference count to 0, but don't write: shouldn't be destroyed yet
  adlb::store $y integer 0
  if { [ adlb::exists $x ] } {
    puts "y exists after refcount 0"
    exit 1
  }

} else {
  adlb::server
}

turbine::finalize

puts OK
