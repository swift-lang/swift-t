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
  set head 1
  set tail 2

  # Note: just two should be sufficient, but make test a little tougher
  # by having multiple cross-server references
  adlb::create $head ref
  adlb::create $tail integer

  # Chain them together
  set prev $head
  for { set x 3 } { $x < 128 } { incr x } {
    adlb::create $x ref
    adlb::store $prev ref $x
    set prev $x
  }
  adlb::store $prev ref $tail
  adlb::store $tail integer 42

  # Now each should have read refcount
  
  # Set reference count of first in chain to 0 to trigger destruction of all
  # With server->server reference counting bug, we'll see a deadlock
  adlb::refcount_incr $head r -1
} else {
  adlb::server
}

adlb::finalize 1

puts OK
