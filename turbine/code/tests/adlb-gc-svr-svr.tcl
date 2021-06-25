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
# Three servers
adlb::init 3 1
adlb::enable_read_refcount

proc build_chain { chain_length head } {
  puts "build_chain: $chain_length <$head>" 
  # Select chain of references across different servers
  adlb::create $head ref

  # Chain them together
  set prev $head
  for { set i 0 } { $i < [ expr $chain_length - 2 ] } { incr i } {
    # Random gaps to get them in different servers, and some neighbours
    # on same
    set gap [ expr int(floor(rand() * 3)) ]
    set x [ expr $prev + $gap + 1 ]
    adlb::create $x ref
    adlb::store $prev ref $x
    set prev $x
  }
  set tail [ expr $prev + 1 ]
  adlb::create $tail integer
  adlb::store $prev ref $tail
  adlb::store $tail integer 42

  # Now each should have read refcount
}


if { ! [ adlb::amserver ] } {
  if { [ adlb::comm_rank ] == 0 } {
    # This rank sets up data for test. Having a single rank set up the
    # data allows us free use of the ID space

    # Seed RNG with constant for reproducibility
    expr srand(12345)

    set chain_length 16
    # Allow for gaps in chain ids so we can have random gaps and self-increments
    set chain_id_spacing 4096
    set chains 500
    set tasks [ list ]
    for { set i 0 } { $i < $chains } { incr i } {
      # Assign each chain contiguous ids so they're distributed over servers
      set head [ expr $i * $chain_id_spacing  + 1 ]
      puts "Chain head: $head"
      build_chain $chain_length $head
      
      # Task will set reference count of first in chain to 0 to trigger
      # destruction of all # With old server->server reference counting
      # bug, we saw a deadlock in all cases.  # Now we hope to trigger
      # some more complex bugs with storm of decrements
      lappend tasks "adlb::read_refcount_decr $head"
    }
    
    foreach task $tasks {
      adlb::put $adlb::RANK_ANY 0 $task 0 1
    }
  }

  # Just execute tasks
  while { 1 } { 
    set cmd [ adlb::get 0 answer_rank ]
    if { [ string length $cmd ] == 0 } break
    eval $cmd
  }
} else {
  adlb::server
}

puts "Rank [ adlb::comm_rank ] done!"

adlb::finalize 1

puts OK
