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

# TURBINE.TCL
# Main control functions

package provide turbine [ turbine::c::version ]

namespace eval turbine {
    namespace import ::turbine::c::rule

    namespace export init start finalize spawn_rule rule


    # Import adlb commands
    namespace import ::adlb::put ::adlb::get ::adlb::RANK_ANY \
            ::adlb::get_priority ::adlb::reset_priority ::adlb::set_priority
    # Re-export adlb commands
    namespace export put get RANK_ANY \
                     get_priority reset_priority set_priority

    # Export work types accessible
    variable WORK_TASK
    namespace export WORK_TASK

    # Mode is SERVER, WORK, or name of asynchronous executor
    variable mode

    # Counts of servers and workers
    variable n_adlb_servers
    variable n_workers

    # Count of particular worker types (dict from type name to count)
    variable n_workers_by_type


    # How to display string values in the log
    variable log_string_mode

    # Whether read reference counting is enabled.  Default to off
    variable read_refcounting_on

    # The language driving the run (default Turbine, may be Swift)
    # Used for error messages
    variable language
    set language Turbine

    # The Turbine Tcl error code
    # Catches known errors from Turbine libraries via Tcl return/catch
    variable error_code
    set error_code 10

    # User function
    # servers: Number of ADLB servers
    # lang: language to use in error messages
    # async_work_types: additional work types for async executors.
    #          These are allocated type numbers from 1 onwards
    proc init { servers {lang ""} {async_work_types {}}} {

        variable language
        
        if { $lang != "" } {
            set language $lang
        }

        assert_control_sanity $servers
        setup_log_string

        reset_priority

        set work_types [ concat [ list WORK ] $async_work_types ]
        
        # Set up work types
        enum WORK_TYPE $work_types
        global WORK_TYPE
        set types [ array size WORK_TYPE ]

        # Set up variables
        variable WORK_TASK
        set WORK_TASK $WORK_TYPE(WORK)

        if { [ info exists ::TURBINE_ADLB_COMM ] } {
            adlb::init $servers $types $::TURBINE_ADLB_COMM
        } else {
            adlb::init $servers $types
        }

        assert_sufficient_procs

        c::init [ adlb::amserver ] [ adlb::rank ] [ adlb::size ]

        setup_mode $servers

        turbine::init_rng

        turbine::init_file_types

        c::normalize

        argv_init
    }

    proc assert_control_sanity { n_adlb_servers } {
        if { $n_adlb_servers <= 0 } {
            error "ERROR: SERVERS==0"
        }
    }
    
    # Setup which mode this and other ranks will be running in
    # Assumes that WORK_TYPE array is created and filled, and that
    # Sets n_workers and n_adlb_servers variables
    proc setup_mode { servers } {

        variable n_workers
        variable n_adlb_servers

        set n_workers [ expr {[ adlb::size ] - $servers } ]
        set n_adlb_servers $servers

        variable n_workers_by_type
        set n_workers_by_type [ dict create ]
        set workers_running_sum 0

        global WORK_TYPE
        global env
        foreach work_type [ lsort [array names WORK_TYPE] ] {
          if { $work_type != "WORK" } {
            set env_var "TURBINE_${work_type}_WORKERS"
            set work_type_count 0
            if { [ info exists env($env_var) ] } {
              set work_type_count $env($env_var)
            }
            incr workers_running_sum $work_type_count
            dict set n_workers_by_type $work_type $work_type_count
          }
        }
        
        if { $workers_running_sum >= $n_workers } {
          error "Too many workers allocated to executor types: \
                  {$n_workers_by_type}.\n \
                  Have $n_workers total workers, allocated 
                  $workers_running_sum already, need at least one to \
                  serve as regular worker"
        }
        
        # Remainder goes to regular workers
        set n_regular_workers [ expr {$n_workers - $workers_running_sum} ]
        dict set n_workers_by_type WORK $n_regular_workers

        debug "WORKER TYPE COUNTS: $n_workers_by_type"

        variable mode
        if { [ adlb::amserver ] == 1 } {
          set mode SERVER
        } elseif { [ adlb::rank ] < $n_regular_workers } {
          # Allocate workers to specific types in order of WORK_TYPE array
          set mode WORK
        } else {
          # Work out which other work type we have
          # Regular workers go first, after that alphabetic order
          set k $n_regular_workers
          foreach work_type [ lsort [array names WORK_TYPE] ] {
            if { $work_type != "WORK" } {
              set n [ dict get $n_workers_by_type $work_type ]
              incr k $n
              if { [ adlb::rank ] < $k } {
                set mode $work_type
                break
              }
            }
          }
        }

        log "MODE: $mode"
        if { [ adlb::rank ] == 0 } {
            log_rank_layout
        }
    }

    proc assert_sufficient_procs { } {
        if { [ adlb::size ] < 2 } {
            error "Too few processes specified by user:\
                    [adlb::size], must be at least 2"
        }
    }

    # Get ADLB work type for name
    proc adlb_work_type { name } {
      global WORK_TYPE
      set adlb_type $WORK_TYPE($name)
      if { ! [ string is integer -strict $adlb_type ] } {
        error "Could not locate ADLB work type for $name"
      }
      return $adlb_type
    }

    proc log_rank_layout { } {
        global WORK_TYPE

        variable n_workers
        variable n_adlb_servers
        variable n_workers_by_type

        set first_worker 0
        set first_server [ expr [adlb::size] - $n_adlb_servers ]
        set last_worker  [ expr $first_server - 1 ]
        set last_server  [ expr [adlb::size] - 1 ]
        log [ cat "WORKERS: $n_workers" \
                  "RANKS: $first_worker - $last_worker" ]

        log [ cat "SERVERS: $n_adlb_servers" \
                  "RANKS: $first_server - $last_server" ]

        if { $n_workers <= 0 } {
            turbine_error "No workers!"
        }
        
        # Report on how workers are subdivided
        set n_regular_workers [ dict get $n_workers_by_type WORK ]
        set first_regular_worker $first_worker
        set last_regular_worker [ expr {$first_regular_worker +
                                        $n_regular_workers - 1} ]
        log [ cat "REGULAR WORKERS: $n_regular_workers" \
                  "RANKS: $first_regular_worker - $last_regular_worker" ]

        set curr_rank $n_regular_workers
        foreach work_type [ lsort [array names WORK_TYPE] ] {
          if { $work_type != "WORK" } {
            set n_x_workers [ dict get $n_workers_by_type $work_type ]
            set first_x_worker $curr_rank
            incr curr_rank $n_x_workers
            set last_x_worker [ expr {$curr_rank - 1} ]
            log [ cat "$work_type WORKERS: $n_x_workers" \
                  "RANKS: $first_x_worker - $last_x_worker" ]
          }
        }
    }

    proc start { args } {

        set rules [ lindex $args 0 ]
        if { [ llength $args ] > 1 } {
            set startup_cmd [ lindex $args 1 ]
        } else {
            set startup_cmd ""
        }

        if { [ catch { enter_mode $rules $startup_cmd } e d ] } {
            fail $e $d
        }
    }

    proc enter_mode { rules startup_cmd } {
        global tcl_version
        if { $tcl_version >= 8.6 } {
          try {
            enter_mode_unchecked $rules $startup_cmd
          } trap {TURBINE ERROR} {msg} {
              turbine::abort $msg
          }
        } else {
          enter_mode_unchecked $rules $startup_cmd
        }
    }

    # Inner function without error trapping
    proc enter_mode_unchecked { rules startup_cmd } {
        variable mode
        switch $mode {
            SERVER  { adlb::server }
            WORK  { worker $rules $startup_cmd}
            default {
              # Must be named executor
              c::async_exec_worker_loop $mode
            }
        }
    }

    # Signal error that is caused by problem in user code
    # I.e. that shouldn't include a stacktrace
    proc turbine_error { msg } {
        global tcl_version
        if { $tcl_version >= 8.6 } {
            throw {TURBINE ERROR} $msg
        } else {
            error $msg
        }
    }

    # Turbine logging contains string values (possibly long)
    # Setting TURBINE_LOG_STRING_MODE truncates these strings
    proc setup_log_string { } {

        global env
        variable log_string_mode

        # Read from environment
        if { [ info exists env(TURBINE_LOG_STRING_MODE) ] } {
            set log_string_mode $env(TURBINE_LOG_STRING_MODE)
        } else {
            set log_string_mode "ON"
        }

        # Check validity - if valid, return without error
        switch $log_string_mode {
            ON  { return }
            OFF { return }
            default {
                if { [ string is integer $log_string_mode ] } {
                    incr log_string_mode -1
                    return
                }
            }
        }

        # Invalid- fall through to error
        error [ join [ "Requires integer or ON or OFF:"
                       "TURBINE_LOG_STRING_MODE=$log_string_mode" ] ]
    }

    proc enable_read_refcount {} {
      adlb::enable_read_refcount
    }

    proc debug { msg } {
        c::debug $msg
    }

    proc finalize { } {
        log "turbine finalizing"
        turbine::c::finalize
        if { [ info exists ::TURBINE_ADLB_COMM ] } {
            adlb::finalize 0
        } else {
            adlb::finalize 1
        }
    }

    # Set servers in the caller's stack frame
    # Used to get tests running, etc.
    proc defaults { } {

        global env
        upvar 1 servers s

        if [ info exists env(ADLB_SERVERS) ] {
            set s $env(ADLB_SERVERS)
        } else {
            set s ""
        }
        if { [ string length $s ] == 0 } {
            set s 1
        }
    }

    # Default error handling for any errors
    # Provides stack trace if error code is not turbine::error_code
    #    Thus useful for internal errors
    # msg: A Tcl error message
    # e: A Tcl error dict
    proc fail { msg d } {
        variable error_code
        set code [ dict get $d -code ]
        if { $code == $error_code } {
            puts "ERROR: $msg"
            puts "CALLING adlb::fail"
            adlb::fail
        } else {
            puts "CAUGHT ERROR:"
            puts $::errorInfo
            puts "CALLING adlb::abort"
            adlb::abort
        }
    }

    # Preferred error handling for known user errors
    # Does not provide a stack trace - nice for users
    # Used by top-level try/trap
    proc abort { msg } {
        variable language
        puts ""
        puts "$language: $msg"
        puts ""
        puts "$language: killing MPI job..."
        adlb::abort
    }

    proc turbine_workers { } {
        variable n_workers
        return $n_workers
    }

    proc turbine_workers_future { output inputs } {
        store_integer $output [ turbine_workers ]
    }

    proc adlb_servers { } {
        variable n_adlb_servers
        return $n_adlb_servers
    }

    proc adlb_servers_future { output inputs } {
        store_integer $output [ adlb_servers ]
    }

    proc check_constants { args } {
      set n [ llength $args ]
      if [ expr { $n % 3 != 0} ] {
        error "Must have multiple of three args to check_constants"
      }
      for { set i 0 } { $i < $n } { incr i 3 } {
        set name [ lindex $args $i ]
        set turbine [ lindex $args [ expr $i + 1 ] ]
        set compiler [ lindex $args [ expr $i + 2 ] ]
        if { $turbine != $compiler } {
          error "Constants emitted by compiler for $name don't match.  \
                 Expected $turbine but emitted $compiler"
        }
      }
    }

}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
