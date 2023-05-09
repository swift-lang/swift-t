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

# WORKER.TCL
# Code executed on worker processes

namespace eval turbine {

    # Main worker loop
    proc standard_worker { rules startup_cmd {mode WORK}} {

        eval $startup_cmd
        set rank [ adlb::comm_rank ]
        if { $rank == 0 } {
            # First rank should start execution
            eval $rules
        }

        if { [ gemtc_alt_worker ] } {
          # Rank alternative gemtc worker
          # TODO: replace with proper gemtc async worker
          return
        }

        set keyword_args [ dict create ]

        set buffer_size_val [ configured_buffer_size $mode ]

        if { $buffer_size_val != "" }  {
          dict append keyword_args buffer_size $buffer_size_val
        }

        global WORK_TYPE
        leader_hook_startup

        set success true
        try {
            c::worker_loop $WORK_TYPE($mode) $keyword_args
        } trap {TURBINE ERROR} e {
            throw {TURBINE ERROR} "user work error:\n$e"
        } on error e {
            set success false
            puts "WORKER ERROR: $::errorInfo"
        }

        leader_hook_shutdown

        if { ! $success } {
            puts "worker throwing error: "
            error "worker loop error"
        }
    }

    proc custom_worker { rules startup_cmd mode } {
        variable addtl_work_types
        if { [ lsearch -exact [ available_executors ] $mode ] == -1 } {
            # Standard worker with custom work type
            standard_worker $rules $startup_cmd $mode
        } else {
            # Must be named async executor
            async_exec_worker $mode $rules $startup_cmd
        }
    }

    # Worker that executes tasks via async executor
    proc async_exec_worker { work_type rules startup_cmd  } {
        global env
        set config_key "TURBINE_${work_type}_CONFIG"
        set config_str ""
        if [ info exists env($config_key) ] {
          set config_str $env($config_key)
        }

        set keyword_args [ dict create ]

        set buffer_size_val [ configured_buffer_size $work_type ]

        if { $buffer_size_val != "" }  {
          dict append keyword_args buffer_size $buffer_size_val
        }

        set buffer_count_val [ configured_buffer_count $work_type ]

        if { $buffer_count_val != "" }  {
          dict append keyword_args buffer_count $buffer_count_val
        }

        async_exec_configure $work_type $config_str

        eval $startup_cmd
        if { [ adlb::comm_rank ] == 0 } {
            # First rank should start execution
            eval $rules
        }


        global WORK_TYPE

        c::async_exec_worker_loop $work_type $WORK_TYPE($work_type) $keyword_args
    }

    # returns empty string for default, or configured task buffer size
    proc configured_buffer_size { {work_type WORK} } {
        global env
        set buffer_size_key "TURBINE_${work_type}_MAX_TASK_SIZE"
        if [ info exists env($buffer_size_key) ] {
          return $env($buffer_size_key)
        } else {
          return ""
        }
    }

    # returns empty string for default, or configured task buffer count
    proc configured_buffer_count { {work_type WORK} } {
        global env
        set buffer_count_key "TURBINE_${work_type}_BUFFER_COUNT"
        if [ info exists env($buffer_count_key) ] {
          return $env($buffer_count_key)
        } else {
          return ""
        }
    }

    proc leader_hook_startup { } {
        global env
        if [ info exists env(TURBINE_LEADER_HOOK_STARTUP) ] {
            if { [ adlb::comm_get leaders ] != [ adlb::comm_get null ] } {
                # I am a leader - eval the hook
                puts "TURBINE_LEADER_HOOK_STARTUP: "
                # $env(TURBINE_LEADER_HOOK_STARTUP)
                try {
                    eval $env(TURBINE_LEADER_HOOK_STARTUP)
                } on error e {
                    puts ""
                    puts "Error in TURBINE_LEADER_HOOK_STARTUP: $e"
                    puts ""
                    exit 1
                }
            }
            # Whether or not a leader, we still need to
            # wait for the leaders to finish the hook
            adlb::worker_barrier
        }
    }

    proc leader_hook_shutdown { } {
        if { [ adlb::comm_get leaders ] == [ adlb::comm_get null ] } {
            # I am not a leader
            return
        }
        global env
        if [ info exists env(TURBINE_LEADER_HOOK_SHUTDOWN) ] {
            puts "TURBINE_LEADER_HOOK_SHUTDOWN: $env(TURBINE_LEADER_HOOK_SHUTDOWN)"
            try {
                eval $env(TURBINE_LEADER_HOOK_SHUTDOWN)
            } on error e {
                puts ""
                puts "Error in TURBINE_LEADER_HOOK_SHUTDOWN: $e"
                puts ""
                exit 1
            }
        }
    }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
