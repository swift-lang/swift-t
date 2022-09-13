
# JOB TCL
# Various application job functionality

namespace eval turbine {

  proc job_srun_tcl { outputs inputs args } {
    set exit_code [ lindex $outputs 0 ]
    set cpj [ lindex $inputs 0 ]
    set ppj [ lindex $inputs 1 ]
    rule $inputs "turbine::job_srun_tcl_body $exit_code $cpj $ppj $args" \
        {*}$args type $turbine::WORK
  }

  proc job_srun_tcl_body { exit_code cpj ppj args } {
    # Unpack args TDs
    set length [ adlb::container_size $args ]
    set tds    [ adlb::enumerate $args dict all 0 ]
    # Retrieve data
    set cpj_value [ adlb::retrieve_integer $cpj ]
    set ppj_value [ adlb::retrieve_integer $ppj ]
    set cmd_value [ list ]
    foreach td in $tds {
      set s [ adlb::retrieve $td ]
      lappend cmd_value $s
    }
    # Run the user code
    set exit_code_value [ job_srun_impl $cpj_value $ppj_value $cmd_value ]
    # Store result
    if { $rank == 0 } {
      store_integer $exit_code $exit_code_value
    }
  }

  proc job_srun_impl { cpj ppj cmd } {
    try {
      exec "srun -n $ppj $cmd"
    } on error e {
      puts "srun failed!"
    }
  }
}
