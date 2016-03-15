
namespace eval f {

    proc f_init_tcl { outputs inputs args } {
        set r [ lindex $outputs 0 ]
        set n [ lindex $inputs  0 ]
        rule $n "f::f_init_tcl_body $r $n" {*}$args type $turbine::WORK
    }

    proc f_init_tcl_body { root n } {
        # Retrieve n
        set n_value [ retrieve_integer $n ]
        # Look up MPI information
        set comm [ turbine::c::task_comm ]
        set task_rank [ adlb::rank $comm ]
        # Run the user code
        f_init $comm $n_value
        # Close the output
        if { $task_rank == 0 } {
            store_integer $root [adlb::rank]
        }
    }

    proc f_task_tcl { outputs inputs args } {
        set z [ lindex $outputs 0 ]
        set k [ lindex $inputs  0 ]
        rule $k "f::f_task_tcl_body $z $k" {*}$args type $turbine::WORK
    }

    proc f_task_tcl_body { z k } {
        # Retrieve k
        set k_value [ retrieve_integer $k ]
        # Look up MPI information
        set comm [ turbine::c::task_comm ]
        set rank [ adlb::rank $comm ]
        # Run the user code
        set z_value [ f_task $k_value ]
        # Store result
        if { $rank == 0 } {
            store_float $z $z_value
        }
    }
}
