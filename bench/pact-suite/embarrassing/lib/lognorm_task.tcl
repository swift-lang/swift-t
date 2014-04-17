package provide lognorm_task 0.0
package require lognorm

namespace eval lognorm_task {
  namespace import ::lognorm::*

  package require turbine

  proc lognorm_task { o in } {
    set i [ lindex $in 0 ]
    set j [ lindex $in 1 ]
    set mu [ lindex $in 2 ]
    set sigma [ lindex $in 3 ]

    turbine::rule [ list $i $j $mu $sigma ] \
        "lognorm_task::lognorm_task_body $o $i $j $mu $sigma"
  }

  proc lognorm_task_body { o i j mu sigma } {
    set i_val [ turbine::retrieve_decr_integer $i ]
    set j_val [ turbine::retrieve_decr_integer $j ]
    set mu_val [ turbine::retrieve_decr_float $mu ]
    set sigma_val [ turbine::retrieve_decr_float $sigma ]

    turbine::store_float $o [ lognorm_task_impl $i_val $j_val $mu_val $sigma_val ]
  }

  proc lognorm_task_impl { i j mu sigma } {
    set secs [ lognorm::sample $mu $sigma ]
    turbine::spin $secs
    return $i
  }
}
