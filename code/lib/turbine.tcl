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
    variable CONTROL_TASK
    namespace export WORK_TASK CONTROL_TASK

    # Mode is ENGINE, WORKER, or SERVER
    variable mode
    variable is_engine

    # Counts of engines, servers, workers
    variable n_adlb_servers
    variable n_engines
    variable n_workers

    # How to display string values in the log
    variable log_string_mode

    # The Turbine Tcl error code
    # Catches known errors from Turbine libraries via Tcl return/catch
    variable error_code

    # Whether read reference counting is enabled.  Default to off
    variable read_refcounting_on

    # User function
    # param e Number of engines
    # param s Number of ADLB servers
    proc init { engines servers } {

        setup_log_string

        variable error_code
        set error_code 10

        reset_priority

        # Set up work types
        enum WORK_TYPE { WORK CONTROL }
        global WORK_TYPE
        set types [ array size WORK_TYPE ]

        # Set up variables
        variable WORK_TASK
        variable CONTROL_TASK
        set WORK_TASK $WORK_TYPE(WORK)
        set CONTROL_TASK $WORK_TYPE(CONTROL)

        if { [ info exists ::TURBINE_ADLB_COMM ] } {
            adlb::init $servers $types $::TURBINE_ADLB_COMM
        } else {
            adlb::init $servers $types
        }
        c::init [ adlb::amserver ] [ adlb::rank ] [ adlb::size ]

        setup_mode $engines $servers

        turbine::init_rng

        adlb::barrier
        c::normalize

        argv_init
    }

    proc setup_mode { engines servers } {

        variable n_adlb_servers
        variable n_engines
        variable n_workers
        set n_adlb_servers $servers
        set n_engines $engines
        set n_workers [ expr {[ adlb::size ] - $servers - $engines} ]


        variable mode
	variable is_engine
        if { [ adlb::rank ] < $engines } {
	    set mode ENGINE
	    set is_engine 1

        } elseif { [ adlb::amserver ] == 1 } {
            set mode SERVER
	    set is_engine 0
        } else {
	    set mode WORKER
	    set is_engine 0
        }

        log "MODE: $mode"
        if { [ adlb::rank ] == 0 } {
            log "ENGINES: $n_engines"
            log "SERVERS: $n_adlb_servers"
            log "WORKERS: $n_workers"
            set adlb_procs [ adlb::size ]
            if { $adlb_procs < 3 } {
              puts "ERROR: too few Turbine processes specified by user:\
                    $adlb_procs, must be at least 3"
              exit 1
            }

            if { $n_adlb_servers <= 0 } {
                puts "ERROR: SERVERS==0"
                exit 1
            }
            if { $n_workers <= 0 } {
                puts "ERROR: WORKERS==0"
                exit 1
            }
        }
    }

    proc start { args } {

        set rules [ lindex $args 0 ]
        if { [ llength $args ] > 1 } {
            set engine_startup [ lindex $args 1 ]
        } else {
            set engine_startup ""
        }

        if { [ catch { enter_mode $rules $engine_startup } e d ] } {
            fail $e $d
        }
    }

    proc enter_mode { rules engine_startup } {

        variable mode
        switch $mode {
            ENGINE  { engine $rules $engine_startup }
            SERVER  { adlb::server }
            WORKER  { worker }
            default { error "UNKNOWN MODE: $mode" }
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

    # Set engines and servers in the caller's stack frame
    # Used to get tests running, etc.
    proc defaults { } {

        global env
        upvar 1 engines e
        upvar 1 servers s

        if [ info exists env(TURBINE_ENGINES) ] {
            set e $env(TURBINE_ENGINES)
        } else {
            set e ""
        }
        if { [ string length $e ] == 0 } {
            set e 1
        }

        if [ info exists env(ADLB_SERVERS) ] {
            set s $env(ADLB_SERVERS)
        } else {
            set s ""
        }
        if { [ string length $s ] == 0 } {
            set s 1
        }
    }

    # Error handling
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

    proc turbine_workers { } {
        variable n_workers
        return $n_workers
    }

    proc turbine_workers_future { output inputs } {
        store_integer $output [ turbine_workers ]
    }

    proc turbine_engines { } {
        variable n_engines
        return $n_engines
    }

    proc turbine_engines_future { output inputs } {
        store_integer $output [ turbine_engines ]
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

    # Get option from rule opts: if not found, return default
    proc opt_get { opts key } {
        switch $key {
            parallelism {
                if [ dict exists $opts parallelism ] {
                    return [ dict get $opts parallelism ]
                } else {
                    return 1
                }
            }
            target {
                if [ dict exists $opts target ] {
                    return [ dict get $opts target ]
                } else {
                    return $::adlb::RANK_ANY
                }
            }
            type {
                if [ dict exists $opts type ] {
                    return [ dict get $opts type ]
                } else {
                    return $::turbine::LOCAL
                }
            }
            default {
                error "rule_opt_get: unknown key: $key"
            }
        }
    }

    # Augment rule so that it can be run on worker
    # args: inputs action opts
    # opts: optional: dict of options: see tcl-turbine.c:Turbine_Rule_Cmd()
    # default: action type: $turbine::LOCAL
    proc spawn_rule { inputs action args } {
        variable is_engine
        global WORK_TYPE

        debug "turbine::rule..."
        if { $is_engine } {
            rule $inputs $action {*}$args
        } elseif { [ llength $inputs ] == 0 } {
            release -1 \
                [ opt_get $args type   ] $action                     \
                [ opt_get $args target ] [ opt_get $args parallelism ]
        } else {
            adlb::put $::adlb::RANK_ANY $WORK_TYPE(CONTROL) \
                [ list rule $inputs $action {*}$args ] \
                [ get_priority ] 1
        }
    }

}
