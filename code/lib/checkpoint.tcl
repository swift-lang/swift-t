
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

# Functions for checkpointing
namespace eval turbine {

  # Can be DISABLED, R, W, RW, depending on whether we are doing nothing
  #  with checkpoints (DISABLED), writing checkpoints for a fresh run (W),
  #  or reading/writing checkpoints after a restore (RW), or reading
  #  old checkpoints but not writing fresh ones (R)
  variable xpt_mode

  # Initialize checkpointing, getting settings from environment
  # Environment variables are:
  # TURBINE_XPT_FILE: file to log to
  # TURBINE_XPT_RESTORE: colon-separated list of files to restore
  # TURBINE_XPT_FLUSH: flush mode
  # TURBINE_XPT_INDEX_MAX: max size in bytes
  proc xpt_init { } {
    variable xpt_mode
    # Defaults
    # Default to periodic_flush
    set flush_mode periodic_flush
    # Default to 1mb
    set xpt_index_max [ expr 1024 * 1024 ]
    set xpt_filename ""
    # xpt_restore is list of files to restore
    set xpt_restore [ list ]

    if [ info exists ::env(TURBINE_XPT_FILE) ] {
      set xpt_filename $::env(TURBINE_XPT_FILE)
    }
    if [ info exists ::env(TURBINE_XPT_RESTORE) ] {
      set xpt_restore [ split $::env(TURBINE_XPT_RESTORE) ":" ]
    }
    if [ info exists ::env(TURBINE_XPT_FLUSH) ] {
      set flush_mode $::env(TURBINE_XPT_FLUSH)
    }
    if [ info exists ::env(TURBINE_XPT_INDEX_MAX) ] {
      set xpt_index_max $::env(TURBINE_XPT_INDEX_MAX) 
      if { ! [ string is integer $xpt_index_max ] } {
        error "TURBINE_XPT_INDEX_MAX must be integer: \"${xpt_index_max}\""
      }
    }

    adlb::xpt_init $xpt_filename $flush_mode $xpt_index_max

    foreach restore_file $xpt_restore {
      # TODO: other args for xpt_restore?
      adlb::xpt_restore $restore_file
    }

    #Determine mode based on what was provided
    if { [ llength $xpt_restore ] > 0 } {
      if { $xpt_filename != "" } {
        set xpt_mode RW
      }
      else {
        set xpt_mode R
      }
    } else {
      if { $xpt_filename != "" } {
        set xpt_mode W
      } else {
        set xpt_mode DISABLED
      }
    }
  }

  proc xpt_restore {} {
    variable xpt_mode
  }

  proc xpt_finalize { } {
    adlb::xpt_finalize 
  }
}
