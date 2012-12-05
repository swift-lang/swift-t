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
      set stdout_dst ">[ dict get $kwopts stdout ]"
    }
    if { [ dict exists $kwopts stderr ] } {
      set stderr_dst "2>[ dict get $kwopts stderr ]"
    }
    exec $cmd {*}$args $stdin_src $stdout_dst $stderr_dst
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
        unpack_args_rec $inner [ expr $nest_level - 1 ] $is_file res
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
