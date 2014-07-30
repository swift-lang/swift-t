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
        if { [ adlb::rank ] == 0 } {
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

        puts "VAL $buffer_size_val"
        if { $buffer_size_val != "" }  {
          dict append keyword_args buffer_size $buffer_size_val
        }

        global WORK_TYPE

        c::worker_loop $WORK_TYPE($mode) $keyword_args
    }

    proc custom_worker { rules startup_cmd mode } {
        variable custom_work_types
        if { [ lsearch -exact $custom_work_types $mode ] != -1 } {
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
        if { [ adlb::rank ] == 0 } {
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
}
