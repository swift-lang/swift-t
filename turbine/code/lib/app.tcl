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
# retry_reput logic credit to Azza Ahmed

namespace eval turbine {

  namespace export unpack_args exec_external poll_mock async_exec_coaster

  proc app_init { } {
    variable app_initialized
    variable app_retries_local
    variable app_retries_reput
    variable app_backoff
    # Artificial random delay (seconds) just before launching each app
    variable app_delay_time

    # ADLB type code number
    variable reput_reply

    if { [ info exists app_initialized ] } return

    set app_initialized 1

    getenv_integer TURBINE_APP_RETRIES_LOCAL 0 app_retries_local
    getenv_integer TURBINE_APP_RETRIES_REPUT 0 app_retries_reput

    getenv_double  TURBINE_APP_DELAY   0 app_delay_time
    getenv_double  TURBINE_APP_BACKOFF 1 app_backoff

    if { $app_delay_time > 0 } {
      if { [ adlb::rank ] == 0 } {
        log "TURBINE_APP_DELAY: $app_delay_time"
      }
    }
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
    app_delay_before
    setup_redirects_c $kwopts stdin_src stdout_dst stderr_dst
    exec_local 0 -1 \
        $stdin_src $stdout_dst $stderr_dst \
        $cmd {*}$args
  }

  proc exec_local { tries_reput reply \
                        stdin_src stdout_dst stderr_dst \
                        cmd args } {
    set tries_local 0
    if { $args eq "{{}}" } {
      set args [ list ]
    }

    set stdios [ stdio_log $stdin_src $stdout_dst $stderr_dst ]
    log** "app:" "\[[c_utils::hostname]\]" io=$stdios ":" $cmd {*}$args

    # Begin local retry loop: break on success
    # On failure, throw an error
    while { true } {
      incr tries_local

      set start [ clock milliseconds ]

      # Assume success
      set success true
      try {
        app_try $tries_local $stdin_src $stdout_dst $stderr_dst \
            $cmd {*}$args
      } trap {TURBINE ERROR} { message } {
        set success false
        try {
          app_retry_reput $message $tries_reput [adlb::rank] \
              $stdin_src $stdout_dst $stderr_dst \
              $cmd {*}$args
          break
        } trap {TURBINE ERROR} { message } {
          turbine_error $message
        }
      }
    }

    if { $success } {
      # app success on this rank!
      set stop [ clock milliseconds ]
      set duration [ format "%0.3f" [ expr ($stop-$start)/1000.0 ] ]
      log "app: duration: $duration"
    }

    # If this was a reput, we need to reply and not report duration
    retry_reply $reply
  }

  proc app_try { tries stdin stdout stderr cmd args } {
    try {
      log** "app: exec:" $cmd {*}$args
      c::sync_exec $stdin $stdout $stderr $cmd {*}$args
      # Success: break out of local retry loop
      return -code break
    } trap {TURBINE ERROR} { message } {
      # Error: possibly try again locally
      app_retry_local $message $tries $cmd $args
    }
  }

  # Try to send this task to another worker
  proc app_retry_reput { message tries_reput reply \
                             stdin_src stdout_dst stderr_dst \
                             cmd args  } {
    variable app_retries_reput
    incr tries_reput

    # Throws an error when exhausted
    app_retry_check "reput" $message $tries_reput $app_retries_reput \
        $cmd $args

    log** "app: reput to ADLB:" $cmd $args
    set payload [ list exec_local $tries_reput [adlb::rank] ]
    lappend payload $stdin_src $stdout_dst $stderr_dst
    lappend payload $cmd {*}$args
    global WORK_TYPE
    adlb::put $adlb::RANK_ANY $WORK_TYPE(WORK) $payload 1 1

    # Wait for response from the receiving worker
    set msg [ adlb::get $WORK_TYPE(REPUT) answer_rank ]
    if { $answer_rank == $adlb::RANK_NULL } {
      log "app: received SHUTDOWN while waiting for reput reply"
      return
    }
    log "app: received reput reply from rank $answer_rank"
  }

  proc retry_reply { reply } {
    if { $reply < 0 } {
      # Not a retry
      return
    }

    global WORK_TYPE
    variable reput_reply
    log "app: sending reput reply to rank $reply"
    adlb::put $reply $WORK_TYPE(REPUT) "SUCCESS" 0 1
  }

  proc app_delay_before { } {
    # Apply random delay before invocation (if user asked for it)
    variable app_delay_time
    if { $app_delay_time > 0 } {
      after [ expr round($app_delay_time * 1000.0 * rand()) ]
    }
  }

  # Throws an error when exhausted
  proc app_retry_local { message tries cmd args } {

    variable app_retries_local
    app_retry_check "local" $message $tries $app_retries_local $cmd {*}$args
    app_delay_retry $tries
  }

  # If there are no retries remaining: we throw an error
  # type: local or reput
  # tries: tries of this type so far
  # max: maximal number of tries of this type
  # message: the last error message from trying to run cmd+args
  proc app_retry_check { type message tries max cmd args } {
    if { $tries < $max } {
      log [ cat "$message: retries $type: $tries/$max " \
                "[ c_utils::hostname ] rank [ adlb::rank ]" ]
    } else {
      if { $max > 0 } {
        log "app: exhausted $type tries"
      }
      if { $args eq "{{{}}}" } { set args {} }
      if { $type eq "local" } {
        set    m "app: error: "
        append m "<" $message "> "
        append m "command: " $cmd " " {*}$args
        turbine_error $m
      } else {
        # We already have the complete error message:
        turbine_error $message
      }
    }
  }

  proc app_delay_retry { tries } {
    variable app_backoff
    set delay [ expr { $app_backoff * pow(2, $tries) * rand() } ]
    after [ expr round(1000 * $delay) ]
  }

  # Set specified vars in outer scope for stdin, stdout and stderr
  # based on parameters present in provided dictionary
  # For use of Tcl's exec command
  proc setup_redirects_tcl { kwopts stdin_var stdout_var stderr_var } {
    # Note: strange behaviour can happen if user args have e.g "<"
    # or ">" or "|" at start
    upvar 1 $stdin_var  stdin_src
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
    upvar 1 $stdin_var  stdin_src
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
