
# JOB TCL
# Various application job functionality

namespace eval turbine {

  proc job_srun_tcl { outputs inputs } {
    set exit_code [ lindex $outputs 0 ]
    set cpj [ lindex $inputs 0 ]
    set ppj [ lindex $inputs 1 ]
    set cmd_line [ lindex $inputs 2 ]
    rule $inputs "turbine::job_srun_tcl_body $exit_code $cpj $ppj $cmd_line" \
        type $turbine::WORK
  }

  proc job_srun_tcl_body { exit_code cpj ppj cmd_line } {
    # Retrieve data (decr?)
    set cpj_value [ retrieve_integer $cpj ]
    set ppj_value [ retrieve_integer $ppj ]
    # Unpack command line
    set D [ adlb::enumerate $cmd_line dict all 0 ]
    set cmd_value [ list ]
    set sorted_keys [ lsort -integer [ dict keys $D ] ]
    foreach k $sorted_keys {
      lappend cmd_value [ dict get $D $k ]
    }
    # Run the user code
    set exit_code_value [ job_srun_impl $cpj_value $ppj_value $cmd_value ]
    # Store result
    store_integer $exit_code $exit_code_value
  }

  proc job_srun_impl { cpj ppj cmd } {
    try {
      puts "turbine: srun: exec: srun -n $ppj $cmd"
      set fp [ open "|srun -n $ppj $cmd" "r" ]
      show fp
      while { [ gets $fp line ] >= 0 } {
        puts "srun: $line"
      }
      close $fp
    } on error e {
      puts "turbine: srun failed!"
      puts "turbine: srun error message begin:"
      puts $e
      puts "turbine: srun error message end."
      return 1
    }
    return 0
  }
}
