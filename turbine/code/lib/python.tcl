
namespace eval turbine {

  proc python_parallel_tcl { outputs inputs args } {
    set result [ lindex $outputs 0 ]
    lassign $inputs code expr
    rule $inputs \
        "turbine::python_parallel_tcl_body $result $code $expr" \
        {*}$args type $turbine::WORK
  }

  proc python_parallel_tcl_body { result code expr } {
    # Retrieve code, expr
    set code_value [ retrieve_string $code ]
    set expr_value [ retrieve_string $expr ]
    # show code expr
    # Look up MPI information
    set comm [ turbine::c::task_comm_int ]
    set rank [ adlb::comm_rank $comm ]
    # Run the user code
    # show code_value expr_value
    set result_value [ python_parallel_persist $comm $code_value $expr_value ]
    if [ python_parallel_error_status ] {
      turbine_error [ format "ERROR in python_parallel(): %s" \
                          [ python_parallel_error_message ] ]
    }

    # show result_value
    # Store result
    if { $rank == 0 } {
      store_string $result $result_value
    }
  }
}
