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
# Turbine APP.TCL

# Functions for launching external apps

namespace eval turbine {

  namespace export unpack_args exec_external poll_mock async_exec_coaster

  proc app_init { } {
    variable app_initialized
    variable app_retries
#    variable app_backoff

    if { [ info exists app_initialized ] } return

    set app_initialized 1
    getenv_integer TURBINE_APP_RETRIES 0 app_retries
#    set app_backoff 0.1
  }

  # Build up a log message with stdio information
  proc stdio_log { stdin_src stdout_dst stderr_dst } {
    set result [ list ]
    if { [ string length $stdin_src  ] > 0 } {
      lappend result "stdin $stdin_src"
    }
    if { [ string length $stdout_dst ] > 0 } {
      lappend result "stdout $stdout_dst"
    }
    if { [ string length $stderr_dst ] > 0 } {
      lappend result "stderr $stderr_dst"
    }
    return $result
  }

  # Run external application
  # cmd: executable to run
  # kwopts: keyword options.  Valid are:
  #         stdout=file stderr=file
  # args: command line args as strings
  # Note: We use sync_exec instead of the Tcl exec command due to
  # an issue on the Cray.  Implemented in sync_exec.c
  proc exec_external { cmd kwopts args } {

    global tcl_version

    app_init

    setup_redirects_c $kwopts stdin_src stdout_dst stderr_dst
    set stdios [ stdio_log $stdin_src $stdout_dst $stderr_dst ]

    set success true
    set tries 0
    # Begin retry loop: break on success
    while { true } {
      incr tries
      log "shell: Azaa: while loop for -$cmd- @- TRIES count: $tries"
#     log "shell: $cmd $args $stdios"
      set start [ clock milliseconds ]
      variable app_retries
      if { $tries == 1 } {
         log "shell Azza: $cmd, case tries == 1"
         app_run $stdin_src $stdout_dst $stderr_dst $cmd $args $tries $app_retries
         continue
      } else {
         # case tries > 1: dispatch execution to a random rank, then see what happens
         set target_rank [ turbine::random_worker ]
         log "shell Azza: case tries (= $tries) > 1, relocate on $target_rank"
         set tcltmp:prio [ turbine::get_priority ]
         adlb::put $target_rank 0 [ list app_run $stdin_src $stdout_dst $stderr_dst $cmd $args $tries $app_retries ]  ${tcltmp:prio} 1
#        Remember {adlb::put my_rank WORK_TYPE "tcl function name" priority parallelism} I still need to code for WORK_TYPE and parallelism
      	 if { $tries > $app_retries } { break }
      }  
    } ;# End while loop
    set stop [ clock milliseconds ]
    set duration [ format "%0.3f" [ expr ($stop-$start)/1000.0 ] ]
    log "shell command duration: $duration"
  }

  proc app_run { stdin stdout stderr cmd args tries total_retries } {
    global tcl_version 
    if { $tcl_version >= 8.6 } {
       log "shell Azza: app_run with cmd = $cmd, tries = $tries on rank = [ adlb::rank ]"
       try {
         c::sync_exec $stdin $stdout $stderr $cmd {*}$args
	 return -code break
       } trap {TURBINE ERROR} { results } {
         # Error: try again
         app_error $tries $total_retries $results $cmd $args 
       }
    } else {
      # Tcl 8.5
      if { ! [ catch { c::sync_exec $stdin_src \
                           $stdout_dst $stderr_dst $cmd {*}$args } \
                   results options ] } {
	return -code break
      } else {
	# Error: try again
	app_error $tries $total_retries $options $cmd {*}$args
     }
    } 
  }

  proc app_error { tries app_retries options cmd args } {
    global tcl_version
    if { $tcl_version >= 8.6 } {
      set msg $options
    } else {
      # Tcl 8.5
      set errorinfo [ dict get $options -errorinfo ]
      set msg "$errorinfo"
    }
    # variable app_retries
    set retry [ expr $tries <= $app_retries ]
    log "shell Azza: here, app_error on cmd = $cmd, with tries = $tries, app_retries = $app_retries, retry = $retry: $tries <= $app_retries, on rank = [ adlb::rank ] "
    if { ! $retry } {
      log "shell Azza: case where retry fails (retry = $retry)"
      turbine_error "app execution failed" on: [ c_utils::hostname ] \
          "\n $msg" "\n command: $cmd $args"
    }
    app_retry $msg $tries $app_retries
  }

  proc app_retry { msg tries app_retries} {
    log "shell Azza: here app_retry, with tries = $tries, app_retries = $app_retries, on rank = [ adlb::rank ]"
    # Retry:
    # variable app_retries
    # variable app_backoff
    set app_backoff 0.1
    log "$msg: retries: $tries/$app_retries on: [ c_utils::hostname ], rank [ adlb::rank ] "
    set delay [ expr { $app_backoff * pow(2, $tries) * rand() } ]
    after [ expr round(1000 * $delay) ]
  }

  # Set specified vars in outer scope for stdin, stdout and stderr
  # based on parameters present in provided dictionary
  # For use of Tcl's exec command
  proc setup_redirects_tcl { kwopts stdin_var stdout_var stderr_var } {
    #Note: strange behaviour can happen if user args have e.g "<"
    # or ">" or "|" at start
    upvar 1 $stdin_var stdin_src
    upvar 1 $stdout_var stdout_dst
    upvar 1 $stderr_var stderr_dst

    # Default to sending stdout/stderr to process stdout/stderr
    set stdin_src "<@stdin"
    set stdout_dst ">@stdout"
    set stderr_dst "2>@stderr"

    if { [ dict exists $kwopts stdin ] } {;
      set stdin_src "<[ dict get $kwopts stdin ]"
    }
    if { [ dict exists $kwopts stdout ] } {
      set dst [ dict get $kwopts stdout ]
      ensure_directory_exists2 $dst
      set stdout_dst ">$dst"
    }
    if { [ dict exists $kwopts stderr ] } {
      set dst [ dict get $kwopts stderr ]
      ensure_directory_exists2 $dst
      set stderr_dst "2>$dst"
    }
  }

  # Set specified vars in outer scope for stdin, stdout and stderr
  # based on parameters present in provided dictionary
  # For use of Turbine's C-based sync_exec command
  proc setup_redirects_c { kwopts stdin_var stdout_var stderr_var } {
    upvar 1 $stdin_var stdin_src
    upvar 1 $stdout_var stdout_dst
    upvar 1 $stderr_var stderr_dst

    # Default to leaving stdin/stdout/stderr unset
    set stdin_src  ""
    set stdout_dst ""
    set stderr_dst ""

    if { [ dict exists $kwopts stdin ] } {;
      set stdin_src "[ dict get $kwopts stdin ]"
    }
    if { [ dict exists $kwopts stdout ] } {
      set dst [ dict get $kwopts stdout ]
      ensure_directory_exists2 $dst
      set stdout_dst "$dst"
    }
    if { [ dict exists $kwopts stderr ] } {
      set dst [ dict get $kwopts stderr ]
      ensure_directory_exists2 $dst
      set stderr_dst "$dst"
    }
  }

  # Launch a coaster job that will execute asynchronously
  # cmd: command to run
  # cmdargs: arguments for command
  # infiles: input files (e.g. for staging in)
  # outfiles: output files (e.g. for staging out)
  # kwopts: options, including input/output redirects and other settings
  # success: a code fragment to run after finishing the
  #         task.  TODO: want to set vars for this continuation to access
  # failure: failure continuation
  proc async_exec_coaster { cmd cmdargs infiles outfiles kwopts success failure } {
    return [ coaster_run $cmd $cmdargs $infiles $outfiles $kwopts $success $failure ]
  }

  # Alternative implementation
  proc ensure_directory_exists2 { f } {
    set dirname [ file dirname $f ]
    if { $dirname == "." } {
      return
    }
    # recursively create
    file mkdir $dirname
  }

  # Unpack arguments from closed container of any nesting into flat list
  # Container must be deep closed (i.e. all contents closed)
  proc unpack_args { container nest_level base_type } {
    set res [ list ]
    unpack_args_rec $container $nest_level $base_type res
    return $res
  }

  proc unpack_args_rec { container nest_level base_type res_var } {
    upvar 1 $res_var res

    if { $nest_level == 0 } {
      error "Nest_level < 1: $nest_level"
    }

    if { $nest_level == 1 } {
      # 1d array
      unpack_unnested_container $container $base_type res
    } else {
      # Iterate in key order
      set contents [ adlb::enumerate $container dict all 0 ]
      set sorted_keys [ lsort -integer [ dict keys $contents ] ]
      foreach key $sorted_keys {
        set inner [ dict get $contents $key ]
        unpack_args_rec $inner [ expr {$nest_level - 1} ] $base_type res
      }
    }
  }

  proc unpack_unnested_container { container base_type res_var } {
    upvar 1 $res_var res

    # Iterate in key order
    set contents [ adlb::enumerate $container dict all 0 ]
    set sorted_keys [ lsort -integer [ dict keys $contents ] ]
    foreach key $sorted_keys {
      set member [ dict get $contents $key ]
      switch $base_type {
        file_ref {
          lappend res [ retrieve_string [ get_file_path $member ] ]
        }
        ref {
          lappend res [ retrieve $member ]
        }
        default {
          lappend res $member
        }
      }
    }
  }

}

# Local Variables:
# mode: tcl
# tcl-indent-level: 2
# End:
