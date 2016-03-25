
package provide funcs_610 0.0

namespace eval funcs_610 {

  proc f { args } {

    set outputs     [ lindex $args 0 ]
    set inputs      [ lindex $args 1 ]

    if { [ llength $args ] > 2 } {
      set parallelism [ lindex $args 3 ]
      set par_arg "parallelism $parallelism"
    } else {
      set par_arg ""
    }

    rule $inputs "funcs_610::f_body $outputs $inputs" \
        type $turbine::WORK {*}$par_arg
  }

  proc f_body { outputs inputs } {
    puts "$outputs _ $inputs"

    set i_value [ retrieve_integer $inputs ]

    set comm [ turbine::c::task_comm ]

    puts "r:[adlb::rank] i:$i_value"
  }
}
