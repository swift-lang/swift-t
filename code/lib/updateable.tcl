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
# functions operating on updateable variables

namespace eval turbine {

  # initialise updateable variable o with provided float value
  # must be initialized before other operations can proceed
  proc init_updateable_float { id val } {
    adlb::store $id float $val 0
  }

  proc lock_loop { id } {
      # Delay time in ms
      set i 0
      while { ! [ adlb::lock $id ] } {
          if { $i >= 1000 } {
              error "Could not lock: $id"
          }
          after $i
          incr i
      }
  }

  proc update_min { x val } {
      rule $val "update_min_body $x $val" \
          name "update_min-$x-$val"
  }
  proc update_min_body { x val } {
    set val2 [ retrieve_decr_float $val ]
    update_min_impl $x $val2
    write_refcount_decr $x
  }

  proc update_min_impl { x val } {

      lock_loop $x
      set old [ adlb::retrieve $x float ]
      if { $old > $val } {
          adlb::store $x float $val 0
      }
      adlb::unlock $x
  }

  proc update_incr { x val } {
      rule $val "update_incr_body $x $val" \
          name "update_incr-$x-$val"
  }
  proc update_incr_body { x val } {
    set val2 [ retrieve_decr_float $val ]
    update_incr_impl $x $val2
    write_refcount_decr $x
  }

  proc update_incr_impl { x val } {
    lock_loop $x
    set old [ adlb::retrieve $x float ]
    adlb::store $x float [ expr {$val + $old} ] 0
    adlb::unlock $x
  }

  proc update_scale { x val } {
    rule $val "update_scale_body $x $val" \
        name "update_scale-$x-$val"
  }
  proc update_scale_body { x val } {
    set val2 [ retrieve_decr_float $val ]
    update_scale_impl $x $val2
    write_refcount_decr $x
  }

  proc update_scale_impl { x val } {
    lock_loop $x
    set old [ adlb::retrieve $x float ]
    adlb::store $x float [ expr {$val * $old} ] 0
    adlb::unlock $x
  }
}
