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

    # Default to sending stdout/stderr to process stdout/stderr
    set stdout_dst ">@stdout"
    set stderr_dst "2>@stderr"
    set stdin_src "<@stdin"

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
    log "shell: $cmd $args $stdin_src $stdout_dst $stderr_dst"
    exec $cmd {*}$args $stdin_src $stdout_dst $stderr_dst

    # Issue #501
    # exec_coaster $cmd {*}$args $stdin_src $stdout_dst $stderr_dst
  }

    # Issue #501
    # proc exec_coaster { args } { 
    #     global auto_path
    #     puts "ap: $auto_path"
    #     puts "exec_coaster: $args"
    #     package require coaster 0.0
    #     exit 1
    # }  
    
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
