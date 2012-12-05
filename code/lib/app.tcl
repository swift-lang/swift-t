# Turbine APP.TCL

# Functions for launching external apps
namespace eval turbine {
  namespace export unpack_args exec_external
  
  # Run external appplication
  # cmd: executable to run
  # args: command line args as strings
  proc exec_external { cmd args } {
    exec $cmd {*}$args >@stdout 2>@stderr
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
