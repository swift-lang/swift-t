# functions operating on updateable variables

namespace eval turbine {

  # initialise updateable variable o with value in future i
  # for now, assume floats
  proc init_updateable { stack o i } {
    #TODO
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

  proc update_min { stack o inputs } {
    set x [ lindex $inputs 0 ]
    set val [ lindex $inputs 1 ]
    rule "update_min-$x-$val" $val \
        $turbine::LOCAL "update_min_body $x $val"
  }
  proc update_min_body { x val } {
    set val2 [ retrieve_float $val ]
    update_min_impl $x $val2
  }

  proc update_min_impl { x val } {

      lock_loop $x
      set old [ turbine::retrieve_float $x ]
      if { $old > $val } {
          adlb::store $x $adlb::FLOAT $val
      }
      adlb::unlock $x
  }

  proc update_incr { stack o inputs } {
    set x [ lindex $inputs 0 ]
    set val [ lindex $inputs 1 ]
    rule "update_incr-$x-$val" $val \
        $turbine::LOCAL "update_incr_body $x $val"
  }
  proc update_incr_body { x val } {
    set val2 [ retrieve_float $val ]
    update_incr_impl $x $val2
  }

  proc update_incr_impl { x val } {
    # TODO: this version has a race condition
    set old [ turbine::retrieve_float $x ]
    adlb::store $x $adlb::FLOAT [ expr $val + $old ]
  }

  proc update_scale { stack o inputs } {
    set x [ lindex $inputs 0 ]
    set val [ lindex $inputs 1 ]
    rule "update_scale-$x-$val" $val \
        $turbine::LOCAL "update_scale_body $x $val"
  }
  proc update_scale_body { x val } {
    set val2 [ retrieve_float $val ]
    update_scale_impl $x $val2
  }

  proc update_scale_impl { x val } {
    # TODO: this version has a race condition
    set old [ turbine::retrieve_float $x ]
    adlb::store $x $adlb::FLOAT [ expr $val * $old ]
  }
}
