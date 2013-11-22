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

package require turbine 0.4.0


turbine::defaults
adlb::init 2 1
adlb::enable_read_refcount

if { ! [ adlb::amserver ] } {
  # Datum ids on two different servers
  set x1 1
  set x2 2
  set x3 3
  set x4 4

  # Note: just two should be sufficient, but make test a little tougher
  # by having multiple cross-server references
  adlb::create $x1 ref
  adlb::create $x2 ref
  adlb::create $x3 ref
  adlb::create $x4 integer

  # Chain them together
  adlb::store $x1 ref $x2
  adlb::store $x2 ref $x3
  adlb::store $x3 ref $x4
  adlb::store $x4 integer 42

  # Now each should have read refcount
  
  # Set reference count of first in chain to 0 to trigger destruction of all
  # With server->server reference counting bug, we'll see a deadlock
  adlb::refcount_incr $x1 r -1
} else {
  adlb::server
}

adlb::finalize 1

puts OK
