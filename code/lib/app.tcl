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
    variable app_backoff

    if { [ info exists app_initialized ] } return

    set app_initialized 1
    getenv_integer TURBINE_APP_RETRIES 0 app_retries
    set app_backoff 0.1
  }

  # Run external application
  # cmd: executable to run
  # kwopts: keyword options.  Valid are:
  #         stdout=file stderr=file
  # args: command line args as strings
  proc exec_external { cmd kwopts args } {

    app_init
    setup_redirects $kwopts stdin_src stdout_dst stderr_dst

    set tries 0
    while { true } {
      log "shell: $cmd $args $stdin_src $stdout_dst $stderr_dst"
      set start [ clock milliseconds ]
      if { ! [ catch { exec $cmd {*}$args $stdin_src $stdout_dst $stderr_dst } \
                 results options ] } {
        # No error: success
        break
      }
      app_error $tries $options $cmd {*}$args
      incr tries
    }
    set stop [ clock milliseconds ]
    set duration [ format "%0.3f" [ expr ($stop-$start)/1000.0 ] ]
    log "shell command duration: $duration"
  }

  proc app_error { tries options cmd args } {
    set details [ dict get $options -errorcode ]
    set einfo [ dict get $options -errorinfo ]
    set ecmd [ list $cmd {*}$args ]
    if { [ lindex $details 0 ] == "CHILDSTATUS" } {
      # Child process failed
      set epid [ lindex $details 1 ]
      set ecode [ lindex $details 2 ]
      set msg "external process $epid failed with exit code $ecode\
            while executing '$ecmd'"
    } elseif { [ lindex $details 0 ] == "POSIX" } {
      set posixcode [ lindex $details 1 ]
      set posixinfo [ lindex $details 2 ]
      set msg "could not launch external command: '$ecmd'\
          error code: $posixcode error info: '$posixinfo'"
    } else {
      set msg "external command failed in unexpected way: '$cmd $args'\
          details: $details error info: '$einfo'"
    }
    variable app_retries
    set retry [ expr $tries < $app_retries ]
    if { ! $retry } {
      turbine_error $msg
    }
    app_retry $msg $tries
  }

  proc app_retry { msg tries } {
    # Retry:
    variable app_retries
    variable app_backoff
    log "$msg: retries: $tries/$app_retries"
    set delay [ expr { $app_backoff * pow(2, $tries) * rand() } ]
    after [ expr round(1000 * $delay) ]
  }

  # Set specified vars in outer scope for stdin, stdout and stderr
  # based on parameters present in provided dictionary
  proc setup_redirects { kwopts stdin_var stdout_var stderr_var } {
    #FIXME: strange behaviour can happen if user args have e.g "<"
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

  # For file f = "/d1/d2/f", ensure /d1/d2 exists
  proc ensure_directory_exists { f } {
      log "ensure_directory_exists: $f"
      set c [ string range $f 0 0 ]
      set A [ file split $f ]
      debug "path components: $A"
      set d [ lreplace $A end end ]
      set p [ join $d "/" ]
      if { [ string equal [ string range $p 0 1 ] "//" ] } {
          # This was an absolute path
          set p [ string replace $p 0 1 "/" ]
      }
      log "checking directory: $p"
      if { ! [ file isdirectory $p ] } {
          log "making directory: $p"
          file mkdir $p
      }
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
