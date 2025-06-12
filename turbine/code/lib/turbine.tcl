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
    namespace import ::adlb::put ::adlb::get ::adlb::RANK_ANY    \
                     ::adlb::get_priority ::adlb::reset_priority \
                     ::adlb::set_priority
    # Re-export adlb commands
    namespace export put get RANK_ANY \
                     get_priority reset_priority set_priority

    # Import executor commands
    namespace import ::turbine::c::noop_exec_* \
                     ::turbine::c::coaster_* \
                     ::turbine::c::async_exec_configure \
                     ::turbine::c::async_exec_names
    namespace export noop_exec_* coaster_* async_exec_configure \
                     async_exec_names

    # Export work types accessible
    variable WORK_TASK
    namespace export WORK_TASK

    # Custom work type list
    variable addtl_work_types
    set addtl_work_types [ list ]

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

    # The language driving the run (default "Turbine", may be "Swift")
    # Used for error messages
    variable language
    set language Turbine

    # The Turbine Tcl error code
    # Catches known errors from Turbine libraries via Tcl return/catch
    variable error_code
    set error_code 10

    # The list of enabled debug categories
    variable debug_categories

    # User function
    # rank_config: If an empty string, configure ranks based on environment.
    #     If an integer, interpret as a server count and do old-style
    #     worker/server split for backwards compatibility.
    #     Otherwise interpret as a custom rank layout object as documented by
    #     the rank_allocation function
    # lang: language to use in error messages:
    #           normally "Swift", defaults to ""
    proc init { rank_config {lang ""} } {

        variable language
        if { $lang != "" } {
            set language $lang
        }

        # Initialize debugging in case other functions want to debug
        variable debug_categories
        c::init_debug
        set debug_categories [ list ]

        debug_set SHUTDOWN true

        # Setup communicator so we can get size later
        if { [ info exists ::TURBINE_ADLB_COMM ] } {
            adlb::init_comm $::TURBINE_ADLB_COMM
        } else {
            adlb::init_comm
        }

        # Ensure all executors are known to system before setting up ranks
        register_all_executors

        if { $rank_config == {}} {
            # Use standard rank configuration mechanism
            set rank_allocation [ rank_allocation [ adlb::comm_size ] ]
        } elseif { [ string is integer -strict $rank_config ] } {
            # Interpret as servers count
            set rank_allocation [ basic_rank_allocation $rank_config ]
        } else {
            # Interpret as custom rank configuration
            set rank_allocation $rank_config
        }

        set servers [ dict get $rank_allocation servers ]
        assert_control_sanity $servers
        setup_log_string

        reset_priority

        # These are the user work types
        set work_types [ work_types_from_allocation $rank_allocation ]
        # REPUT is a special internal work type used by app.tcl reputs
        lappend work_types REPUT
        debug "work_types: $work_types"
        # Set up work types
        enum WORK_TYPE $work_types
        global WORK_TYPE
        set types [ array size WORK_TYPE ]

        # Set up variables
        variable WORK_TASK
        set WORK_TASK $WORK_TYPE(WORK)

        adlb::init $servers $types

        assert_sufficient_procs

        c::init [ adlb::amserver ] \
            [ adlb::comm_rank ] [ adlb::comm_size ]

        setup_mode $rank_allocation $work_types

        # Initialize Turbine features
        turbine::init_rng
        turbine::init_file_types
        c::normalize
        app_init
        turbine::init_cmds
        argv_init
    }

    proc assert_control_sanity { n_adlb_servers } {
        if { $n_adlb_servers <= 0 } {
            error "ERROR: SERVERS==0"
        }
    }

    # Determine rank allocation to workers/servers based on environment
    # vars, etc
    # adlb_size: the number of ranks in the ADLB communicator
    #
    # Returns a rank allocation object, a dict with the following keys:
    # TODO: update info here
    # servers: the number of ADLB servers
    # workers: the total number of ADLB workers
    # workers_by_type: a tcl dictionary mapping work type to the number
    #         of workers. Keys can be an executor name, or WORK for
    #         a generic CPU worker.  The counts will sum to workers.
    #         Only types with at least one workers will be included.
    proc rank_allocation { adlb_size } {
        global env
        if [ info exists env(ADLB_SERVERS) ] {
            set n_servers $env(ADLB_SERVERS)
        } else {
            set n_servers 1
        }

        set n_workers_by_type [ dict create ]
        set n_workers [ expr { $adlb_size - $n_servers } ]

        if { $n_workers <= 0 } {
            turbine_fail "You have $n_workers workers!\n"     \
                "Check your MPI configuration, "              \
                "there may be a mix of MPICH and OpenMPI.\n"  \
                "Also note that: ADLB_SERVERS=$n_servers\n"   \
                "                world size:  $adlb_size\n\n"
        }

        if { $n_workers < $n_servers } {
            turbine_fail "You have more ADLB servers than workers!\n"
        }

        set workers_running_sum 0

        variable addtl_work_types
        set other_work_types [ concat $addtl_work_types ]

        foreach work_type $other_work_types {
          set env_var "TURBINE_[ string toupper ${work_type} ]_WORKERS"
          set worker_count 0
          if { [ info exists env($env_var) ] } {
            set worker_count $env($env_var)
          }

          debug "$work_type: $worker_count workers"

          if { $worker_count > 0 } {
            # Only include enabled workers
            incr workers_running_sum $worker_count
            dict set n_workers_by_type $work_type $worker_count
          }
        }

        if { $workers_running_sum >= $n_workers } {
          turbine_fail "Too many workers allocated to custom work types! \n" \
              "counts: " [ report_work_type_counts $n_workers_by_type ] "\n" \
              "Total workers:  $n_workers \n" \
              "Custom workers: $workers_running_sum \n" \
              "Need at least one regular worker."
        }

        # Remainder goes to regular workers
        set n_regular_workers [ expr {$n_workers - $workers_running_sum} ]
        dict set n_workers_by_type WORK $n_regular_workers

        debug "WORKER TYPE COUNTS: $n_workers_by_type"

        return [ dict create servers $n_servers workers $n_workers \
                             workers_by_type $n_workers_by_type ]
    }

    proc report_work_type_counts { n_workers_by_type } {
        set result [ list ]
        dict for { k v } $n_workers_by_type {
            lappend result "${k}:${v}"
        }
        return $result
    }

    # Return names of all registered async executors
    proc available_executors {} {
      return [ async_exec_names ]
    }

    # Register all async executors
    proc register_all_executors {} {
      noop_exec_register
      coaster_register

      variable addtl_work_types
      # Also ensure async executors are available
      foreach executor [ available_executors ] {
        if { [ lsearch -exact $addtl_work_types $executor ] == -1 } {
          lappend addtl_work_types $executor
        }
      }
    }

    # Basic rank allocation with only servers and regular workers
    proc basic_rank_allocation { servers } {
        set workers [ expr {[ adlb::comm_size ] - $servers } ]
        return [ dict create servers $servers workers $workers \
                 workers_by_type [ dict create WORK $workers ] ]
    }

    # Return list of work types in canonical order based on rank layout
    # Also checks that all requested executors are assigned a work type
    proc work_types_from_allocation { rank_allocation } {
        set workers_by_type [ dict get $rank_allocation workers_by_type ]

        set work_types [ dict keys $workers_by_type ]

        set work_pos [ lsearch -exact $work_types WORK ]

        # Put in canonical lexical order without WORK
        set executors [ lsort [ lreplace $work_types $work_pos $work_pos ] ]

        return [ concat [ list WORK ] $executors ]
    }

    # Setup which mode this and other ranks will be running in
    #
    # rank_allocation: a rank allocation object
    # work_types: list of ADLB work type names
    #             (matching those in rank_allocation)
    #             with list index corresponding to integer ADLB work type
    #
    # Sets n_workers, n_workers_by_type, and n_adlb_servers variables
    proc setup_mode { rank_allocation work_types } {

        variable n_workers
        variable n_adlb_servers

        set n_workers [ dict get $rank_allocation workers ]
        set n_adlb_servers [ dict get $rank_allocation servers ]

        variable n_workers_by_type
        set n_workers_by_type [ dict get $rank_allocation workers_by_type ]
        set n_regular_workers [ dict get $n_workers_by_type WORK ]

        variable mode
        if { [ adlb::amserver ] == 1 } {
          set mode SERVER
        } else {
          # Work out which other work type we have
          set k 0
          foreach work_type $work_types {
            set n [ dict get $n_workers_by_type $work_type ]
            incr k $n
            if { [ adlb::comm_rank ] < $k } {
              set mode $work_type
              break
            }
          }
        }

        debug "MODE: $mode"
        if { [ adlb::comm_rank ] == 0 } {
            log_rank_layout $work_types
        }
    }

    proc assert_sufficient_procs { } {
        if { [ adlb::comm_size ] < 2 } {
            error "Too few processes specified by user:\
                    [adlb::comm_size], must be at least 2"
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

    proc log_rank_layout { work_types } {

        variable n_workers
        variable n_adlb_servers
        variable n_workers_by_type

        log "WORK TYPES: $work_types"

        set first_worker 0
        set first_server [ expr [adlb::comm_size] - $n_adlb_servers ]
        set last_worker  [ expr $first_server - 1 ]
        set last_server  [ expr [adlb::comm_size] - 1 ]
        log [ cat "WORKERS: $n_workers" \
                  "RANKS: $first_worker - $last_worker" ]
        log [ cat "SERVERS: $n_adlb_servers" \
                  "RANKS: $first_server - $last_server" ]

        if { $n_workers <= 0 } {
            turbine_error "No workers!"
        }

        # Remove REPUT for the following log message
        set work_types_user [ lreplace $work_types end end ]

        # Report on how workers are subdivided for user work types
        set curr_rank 0
        foreach work_type $work_types_user {
          set n_x_workers [ dict get $n_workers_by_type $work_type ]
          set first_x_worker $curr_rank
          incr curr_rank $n_x_workers
          set last_x_worker [ expr {$curr_rank - 1} ]
          log [ cat "$work_type WORKERS: $n_x_workers" \
                "RANKS: $first_x_worker - $last_x_worker" ]
        }
    }

    # Declare custom work types to be implemented by regular worker.
    # Must be declared before turbine::init
    proc declare_custom_work_types { args } {
        variable addtl_work_types
        foreach work_type $args {
          # Avoid duplicates
          if { [ lsearch -exact $addtl_work_types $work_type ] == -1 } {
            lappend addtl_work_types $work_type
          }
        }
    }


    # Check that all executors in list have assigned workers and throw
    # error otherwise.  Run after turbine::init
    proc check_can_execute { exec_names } {
        variable n_workers_by_type
        foreach exec_name $exec_names {
            if { ( ! [ dict exists $n_workers_by_type $exec_name ] ) ||
                 [ dict get $n_workers_by_type $exec_name ] <= 0 } {
                turbine_fail "Custom work types error: " \
                        "Executor $exec_name has no assigned workers!\n" \
                        "Set environment variable TURBINE_${exec_name}_WORKERS " \
                        "to some number of workers."
            }
        }
    }

    proc start { args } {
        # Start checkpointing before servers go into loop
        turbine::xpt_init2

        set rules [ lindex $args 0 ]
        if { [ llength $args ] > 1 } {
            set startup_cmd [ lindex $args 1 ]
        } else {
            set startup_cmd ""
        }

        set success true
        if { [ catch { enter_mode $rules $startup_cmd } e d ] } {
          set success false
        }

        turbine::xpt_finalize2

        if { ! $success } {
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
            SERVER  {
              try {
                adlb::server
              } on error e {
                turbine_error "ADLB server exited with error"
              }
            }
            WORK  { standard_worker $rules $startup_cmd }
            default {
              custom_worker $rules $startup_cmd $mode
            }
        }
    }

    # Signal error that is caused by problem in user code
    # I.e. that shouldn't include a Tcl stack trace
    proc turbine_error { args } {
        global tcl_version
        # puts "turbine_error: $args"
        set msg [ join $args ]
        if { $tcl_version >= 8.6 } {
            throw {TURBINE ERROR} $msg
        } else {
            error $msg
        }
    }

    proc turbine_fail { args } {
        if { [ adlb::comm_rank ] == 0 } {
            puts* $turbine::language ": " {*}$args
        }
        exit 1
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

    # Basic debugging function
    # If #args == 1, then just print that as a message
    # If #args == 2, the first argument is the category,
    #                the second argument is the message,
    #                and only print the message if the category
    #                has been enabled with debug_set
    proc debug { args } {
      variable debug_categories
      set argc [ llength $args ]
      if { $argc == 1 } {
        c::debug $args
      } elseif { $argc == 2 } {
        lassign $args category msg
        if { [ lsearch $debug_categories $category ] >= 0 } {
          c::debug $msg
        }
      } else {
        error "Bad arguments to debug (count=$argc) : $args"
      }
    }

    # Enable debugging on the given category
    # category: any string
    # enabled: true to enable, false to disable
    proc debug_set { category enabled } {
      variable debug_categories
      if $enabled {
        if { [ lsearch $debug_categories $category ] == -1 } {
          lappend debug_categories $category
        } else {
          debug [ cat "debug: warning: " \
                      "duplicate enable of debug category: $category" ]
        }
      } else {
        set index [ lsearch $debug_categories $category ]
        if { $index == -1 } {
          debug [ cat "debug: warning: " \
                      "attempted removal of non-existent category: " \
                      $category ]
        } else {
          set debug_categories \
              [ lreplace debug_categories $index $index ]
        }
      }
    }

    proc debug_enabled { category } {
      variable debug_categories
      if { [ lsearch $debug_categories $category ] == -1 } {
        return false
      } else {
        return true
      }
    }

    proc finalize { } {
        log "turbine finalizing"
        turbine::final_cmds
        mktemp_cleanup
        turbine::c::finalize
        if { [ info exists ::TURBINE_ADLB_COMM ] } {
            adlb::finalize 0
        } else {
            adlb::finalize 1
        }
      if [ debug_enabled SHUTDOWN ] {
        # printf_local "adlb finalized at: %0.4f" [ c::log_time ]
      }
    }

    # DEPRECATED
    # Set servers in the caller's stack frame to {} to invoke new rank
    # allocation method upon init.
    # Used to get tests running, etc.
    proc defaults { } {

        upvar 1 servers s

        set s {}
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
        puts "$language: Aborting MPI job..."
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

    # Get environment variable or assign default
    # Result goes into upvar on output
    # Asserts it is an integer
    # Returns 0 if used default, else 1
    proc getenv_integer { key dflt output } {
        upvar $output result
        return [ getenv_type $key $dflt result integer ]
    }

    proc getenv_double { key dflt output } {

        upvar $output result
        return [ getenv_type $key $dflt result double ]
    }

    proc getenv_type { key dflt output type } {
        global env
        upvar $output result

        if { ! [ info exists env($key) ] ||
             [ string length $env($key) ] == 0 } {
            set result $dflt
            return 0
        }
        if { ! [ string is $type $env($key) ] } {
            turbine_error \
                "Environment variable $key must be of type $type. " \
                "Value: '$env($key)'"
        }

        # Normal case: variable exists and is of correct type
        set result $env($key)
        return 1
    }

    # Initialize user modules
    proc init_cmds { } {
        global turbine_init_cmds
        if { ! [ info exists turbine_init_cmds ] } return
        foreach cmd $turbine_init_cmds {
            eval $cmd
        }
    }

    # Finalize user modules
    proc final_cmds { } {
        global turbine_final_cmds
        if { ! [ info exists turbine_final_cmds ] } return
        foreach cmd $turbine_final_cmds {
            eval $cmd
        }
    }

  # Return Tcl time in seconds as float
  # If argument is provided, subtract that from current time
  proc tcl-time { args } {
    set t [ expr [ clock milliseconds ] / 1000.0 ]
    if [ llength $args ] {
      set t [ expr $t - $args ]
    }
    return $t
  }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
