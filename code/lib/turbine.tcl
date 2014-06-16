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

    # Mode is WORKER, or SERVER
    variable mode

    # Counts of servers and workers
    variable n_adlb_servers
    variable n_workers

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

    proc setup_mode { servers } {

        variable n_workers
        variable n_adlb_servers

        set n_workers [ expr {[ adlb::size ] - $servers } ]
        set n_adlb_servers $servers

        variable mode
        if { [ adlb::amserver ] == 1 } {
            set mode SERVER
        } else {
	    set mode WORKER
        }

        log "MODE: $mode"
        if { [ adlb::rank ] == 0 } {
            log_rank_layout
        }
    }

    proc assert_sufficient_procs { } {
        if { [ adlb::size ] < 2 } {
            error "Too few Turbine processes specified by user:\
                    [adlb::size], must be at least 2"
        }
    }

    proc log_rank_layout { } {

        variable n_workers
        variable n_adlb_servers

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
            WORKER  { worker $rules $startup_cmd}
            default { error "UNKNOWN MODE: $mode" }
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
