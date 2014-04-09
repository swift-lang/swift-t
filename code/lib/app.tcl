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
  namespace export unpack_args exec_external

  # Run external appplication
  # cmd: executable to run
  # kwopts: keyword options.  Valid are:
  #         stdout=file stderr=file
  # args: command line args as strings
  proc exec_external { cmd kwopts args } {
    #FIXME: strange behaviour can happen if user args have e.g "<"
    # or ">" or "|" at start

    setup_redirects $kwopts stdin_src stdout_dst stderr_dst
    log "shell: $cmd $args $stdin_src $stdout_dst $stderr_dst"

    # TODO: remove this - STC will call a coasters-specific launch function
    #       directly
    if {[string match "coaster*" $cmd]} {
      set cmd [string triml $cmd "coaster"]
      exec_coaster $cmd $stdin_src $stdout_dst $stderr_dst {*}$args
    } else {
      set start [ clock milliseconds ]
      if { [ catch { exec $cmd {*}$args $stdin_src $stdout_dst $stderr_dst } ] } {
        turbine_error "external command failed: $cmd $args"
      }
      set stop [ clock milliseconds ]
      set duration [ format "%0.3f" [ expr ($stop-$start)/1000.0 ] ]
      log "shell command duration: $duration"
    }
  }

  # Set specified vars in outer scope for stdin, stdout and stderr
  # based on parameters present in provided dictionary
  proc setup_redirects { kwopts stdin_var stdout_var stderr_var } {
    upvar 1 $stdin_var stdin_src
    upvar 1 $stdout_var stdout_dst
    upvar 1 $stderr_var stderr_dst

    # Default to sending stdout/stderr to process stdout/stderr
    set stdin_src "<@stdin"
    set stdout_dst ">@stdout"
    set stderr_dst "2>@stderr"

    if { [ dict exists $kwopts stdin ] } {
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

  # Launch a coasters job that will execute asynchronously
  # cmd: command to run
  # outfiles: list of output files
  # TODO: also list input files?
  # cmdargs: arguments for command
  # kwopts: options, including input/output redirects and other settings
  # continuation: optionally, a code fragment to run after finishing the
  #         task.  TODO: may want to assume that this is a function call
  #         so we can concatenate any output arguments to string?
  proc async_exec_coasters { cmd outfiles cmdargs kwopts {continuation {}}} {
    setup_redirects $kwopts stdin_src stdout_dst stderr_dst

    # Check to see if we were passed continuation
    set has_continuation [ expr [ string length $continuation ] > 0 ]

    # TODO: handle rest
    error "async_exec_coasters not implemented"
  }

  #Issue #501
  proc exec_coaster { cmd stdin_src stdout_dst stderr_dst args} {
    log "exec_coaster: cmd : $cmd"
    log "exec_coaster: args : $args"

    set stdout_dst [string trim $stdout_dst <>]
    if { $stdout_dst == "@stdout" } {
      log "exec_coaster : stdout not defined, setting to empty"
      set stdout_dst ""
    }
    log "exec_coaster: stdout_dst : $stdout_dst"

    set stderr_dst [string trim $stderr_dst 2>]
    if { $stderr_dst == "2>@stderr" } {
      log "exec_coaster : stdout not defined, setting to empty"
      set stderr_dst ""
    }
    log "exec_coaster: stderr_dst : $stderr_dst"

    package require coaster 0.0

    set loop_ptr [CoasterSWIGLoopCreate]
    set client_ptr [CoasterSWIGClientCreate $loop_ptr 140.221.8.81:34959]
    set x [CoasterSWIGClientSettings $client_ptr "SLOTS=1,MAX_NODES=1,JOBS_PER_NODE=2,WORKER_MANAGER=passive"]
    log "exec_coaster: Error code from CoasterSWIGClientSettings $x"

    # Job stuff
    set job1 [CoasterSWIGJobCreate $cmd]

    #CoasterSWIGJobSettings job_obj dir args attributes env_vars stdout_loc stderr_loc"
    log "exec_coaster : CoasterSWIGJobSettings $job1 \"\" $args \"\" \"\" $stdout_dst $stderr_dst "
    set rcode [CoasterSWIGJobSettings $job1 "" $args "" "" $stdout_dst $stderr_dst]

    set rcode [CoasterSWIGSubmitJob $client_ptr $job1]
    log "exec_coaster: Job1 submitted"

    log "exec_coaster: Waiting for Job1"
    set rcode [CoasterSWIGWaitForJob $client_ptr $job1]
    log "exec_coaster: Job1 complete"

    set rcode [CoasterSWIGClientDestroy $client_ptr]

    set rcode [CoasterSWIGLoopDestroy $loop_ptr]
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
  proc unpack_args { container nest_level is_file } {
    set res [ list ]
    unpack_args_rec $container $nest_level $is_file res
    return $res
  }

  proc unpack_args_rec { container nest_level is_file res_var } {
    upvar 1 $res_var res

    if { $nest_level == 0 } {
      error "Nest_level < 1: $nest_level"
    }

    if { $nest_level == 1 } {
      # 1d array
      unpack_unnested_container $container $is_file res
    } else {
      # Iterate in key order
      set contents [ adlb::enumerate $container dict all 0 ]
      set sorted_keys [ lsort -integer [ dict keys $contents ] ]
      foreach key $sorted_keys {
        set inner [ dict get $contents $key ]
        unpack_args_rec $inner [ expr {$nest_level - 1} ] $is_file res
      }
    }
  }

  proc unpack_unnested_container { container is_file res_var } {
    upvar 1 $res_var res

    # Iterate in key order
    set contents [ adlb::enumerate $container dict all 0 ]
    set sorted_keys [ lsort -integer [ dict keys $contents ] ]
    foreach key $sorted_keys {
      set member [ dict get $contents $key ]
      if { $is_file } {
        lappend res [ retrieve_string [ get_file_path $member ] ]
      } else {
        lappend res [ retrieve $member ]
      }
    }
  }

}
