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
    # Import adlb commands
    namespace import ::adlb::get

    # Main worker loop
    proc worker { rules startup_cmd } {

        eval $startup_cmd
        if { [ adlb::rank ] == 0 } {
            # First rank should start execution
            eval $rules
        }
        
        # Alternative GEMTC worker is enabled by environment variable
        # TURBINE_GEMTC_WORKER=1, or another non-zero value
        # An empty string is treated as false, other values are invalid
        global env
        if { [ info exists env(TURBINE_GEMTC_WORKER) ] &&
             $env(TURBINE_GEMTC_WORKER) != "" } {
            set gemtc_setting $env(TURBINE_GEMTC_WORKER)
            if { ! [ string is integer -strict $gemtc_setting ] } {
              error "Invalid TURBINE_GEMTC_WORKER setting, must be int:\
                     ${gemtc_setting}"
            }

            if { $gemtc_setting } {
             gemtc_worker
             return
            }
        }

        global WORK_TYPE

        c::worker_loop $WORK_TYPE(WORK)
    }
}
