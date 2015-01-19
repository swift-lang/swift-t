
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
  #  or reading/writing checkpoints after a reload (RW), or reading
  #  old checkpoints but not writing fresh ones (R)
  variable xpt_mode

  # Don't wrap xpt_write or xpt_lookup
  namespace import ::adlb::xpt_write ::adlb::xpt_lookup

  namespace export xpt_init xpt_write_enabled xpt_write \
               xpt_lookup_enabled xpt_lookup xpt_finalize

  # Placeholder retained - old versions of STC generated it
  proc xpt_init { } {

  }

  # Placeholder retained - old versions of STC generated it
  proc xpt_finalize { } {
  }

  # Initialize checkpointing, getting settings from environment.
  # Should be called immediately before turbine startup and before
  # servers go into ADLB server loop
  # Environment variables are:
  # TURBINE_XPT_FILE: file to log to
  # TURBINE_XPT_RELOAD: colon-separated list of files to reload
  # TURBINE_XPT_FLUSH: flush mode
  # TURBINE_XPT_INDEX_MAX: max size in bytes
  proc xpt_init2 { } {
    variable xpt_mode
    # Defaults
    # Default to periodic_flush
    set flush_mode periodic_flush
    # Default to 1mb
    set xpt_index_max [ expr 1024 * 1024 ]
    set xpt_filename ""
    # xpt_reload is list of files to reload
    set xpt_reload [ list ]

    if [ info exists ::env(TURBINE_XPT_FILE) ] {
      set xpt_filename $::env(TURBINE_XPT_FILE)
    }
    if [ info exists ::env(TURBINE_XPT_RELOAD) ] {
      # must qualify split to avoid clash with turbine::split
      set xpt_reload [ ::split $::env(TURBINE_XPT_RELOAD) ":" ]
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

    # Note: don't get servers to load checkpoint data because they're
    # needed to serve requests
    if { ! [ adlb::amserver ] } {
      foreach reload_file $xpt_reload {
        set loader_rank [ adlb::worker_rank ]
        set loaders [ adlb::workers ]
        log "Reloading checkpoint file $reload_file loader $loader_rank/$loaders"
        set reload_stats [ adlb::xpt_reload $reload_file $loader_rank $loaders ]
        log "Finished reloading checkpoint file $reload_file"
        log "Reload stats for $reload_file: $reload_stats"
      }

      if { [ llength $xpt_reload ] > 0 } {
        # Wait for everyone to finish loading
        adlb::worker_barrier
        log "Finished loading checkpoint files on all workers"
      }
    }

    #Determine mode based on what was provided
    if { [ llength $xpt_reload ] > 0 } {
      if { $xpt_filename != "" } {
        set xpt_mode RW
      } else {
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

  # Return true if we are actually keeping an index of checkpoints
  proc xpt_lookup_enabled { } {
    variable xpt_mode
    switch $xpt_mode {
      R -
      RW {
        return 1
      }
      W -
      DISABLED {
        return 0
      }
      default {
        error "Unknown checkpoint mode $xpt_mode"
      }
    }
  }

  # Return true if we are logging data
  proc xpt_write_enabled { } {
    variable xpt_mode
    switch $xpt_mode {
      W -
      RW {
        return 1
      }
      R -
      DISABLED {
        return 0
      }
      default {
        error "Unknown checkpoint mode $xpt_mode"
      }
    }
  }

  proc xpt_finalize2 {} {
    adlb::xpt_finalize
  }
}
