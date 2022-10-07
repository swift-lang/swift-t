
# JOB TCL
# Various application job functionality

namespace eval turbine {

  proc job_srun_tcl { outputs inputs } {
    set exit_code [ lindex $outputs 0 ]
    set cpn  [ lindex $inputs 0 ]
    set cpj  [ lindex $inputs 1 ]
    set ppj  [ lindex $inputs 2 ]
    set bind [ lindex $inputs 3 ]
    set cmd_line [ lindex $inputs 4 ]
    rule $inputs \
        "turbine::job_srun_tcl_body $exit_code $cpn $cpj $ppj $bind $cmd_line" \
        type $turbine::WORK
  }

  proc job_srun_tcl_body { exit_code cpn cpj ppj bind cmd_line } {
    # Retrieve data (decr?)
    set cpn_value  [ retrieve_integer $cpn ]
    set cpj_value  [ retrieve_integer $cpj ]
    set ppj_value  [ retrieve_integer $ppj ]
    set bind_value [ retrieve_integer $bind ]
    # Unpack command line
    set D [ adlb::enumerate $cmd_line dict all 0 ]
    set cmd_value [ list ]
    set sorted_keys [ lsort -integer [ dict keys $D ] ]
    foreach k $sorted_keys {
      lappend cmd_value [ dict get $D $k ]
    }
    # Run the user code
    set exit_code_value \
        [ job_srun_impl $cpn_value $cpj_value $ppj_value $bind_value $cmd_value ]
    # Store result
    store_integer $exit_code $exit_code_value
  }

  proc job_srun_impl { cpn cpj ppj bind cmd } {
    # Setup and run the job.  Return a unix exit code.
    global env
    puts "turbine: srun: job_srun ..."

    if $bind {
      set cpu_bind [ bind_mask_cpu $cpn $cpj $ppj ]
    } else {
      set cpu_bind ""
    }

    puts "turbine: srun: job_srun -n $ppj -N 1 $cpu_bind $cmd"
    puts "turbine: srun:   in PWD: $env(PWD)"
    try {
      # Run the user job!  (with pipe to capture output)
      set fp [ open "|srun -n $ppj -N 1 $cpu_bind $cmd 2>@1" "r" ]
      while { [ gets $fp line ] >= 0 } {
        puts "srun: $line"
      }
      close $fp
    } on error e {
      job_srun_error $e
      return 1
    }
    return 0
  }

  proc bind_mask_cpu { cpn cpj ppj } {
    # Set up the SLURM cpu binding
    global env
    set cpu_bind "--cpu-bind=verbose,mask_cpu:"
    set offset $env(ADLB_RANK_OFFSET)
    set ppn $env(PPN)
    # puts "offset=$offset ppn=$ppn cpn=$cpn"
    show offset ppn cpn cpj ppj

    set L [ list ]
    set start [ expr $offset * $cpj ]
    set spacing [ expr $cpj / $ppj ]
    set cpj_max [ expr $cpn / $ppn ]
    show cpj_max
    set start [ expr $cpj_max * $offset ]
    # set S1 [ contig $start $cpj_max ]
    # show S1
    set step [ expr $cpj_max / $cpj ]
    set S2 [ contig $start $cpj $step ]
    show step S2
    set K [ fragment $S2 $ppj ]
    show K

    # set cpu_ids [ join $L "," ]
    # append cpu_bind $cpu_ids
    set masks [ list ]
    foreach chunk $K {
      set mask [ list2mask $chunk ]
      show mask
      lappend masks $mask
    }
    show masks
    append cpu_bind [ join $masks "," ]
    return $cpu_bind
  }

  proc job_srun_error { e } {
    puts "turbine: srun failed!"
    puts "turbine: srun error message begin:"
    puts $e
    puts "turbine: srun error message end."
  }

  proc list2mask { L } {
    set A 0
    foreach i $L {
      incr A [ expr 2 ** $i ]
    }
    puts $A
    # printf "bitmap: %b" $A
    return [ format "0x%X" $A ]
  }
}
