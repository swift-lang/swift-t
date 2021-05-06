namespace eval f {

    proc f_tcl { outputs inputs args } {
        set z [ lindex $outputs 0 ]
        set k [ lindex $inputs 0 ]
        rule $k "f::f_tcl_body $z $k" {*}$args type $turbine::WORK
    }

    proc f_tcl_body { z k } {
        # Retrieve k
        set k_value [ retrieve_integer $k ]
        # Look up MPI information
        set comm [ turbine::c::task_comm_int ]
        set rank [ adlb::comm_rank $comm ]
        # Run the user code
        set z_value [ f $comm $k_value ]
        # Store result
        if { $rank == 0 } {
            store_float $z $z_value
        }
    }
}
