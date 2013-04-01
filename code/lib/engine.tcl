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

# ENGINE.TCL
# Code executed on engine processes

namespace eval turbine {

    proc engine { rules startup } {

        global WORK_TYPE

        turbine::c::engine_init
        ::eval $startup
        if { [ adlb::rank ] == 0 } {
            eval $rules
        }

        turbine::c::push

        while {true} {
            while {true} {
                # Do local work until we have none
                set ready [ turbine::c::ready ]
                if { [ llength $ready ] == 0 } break
                foreach {transform} $ready {
                    set L [ turbine::c::pop $transform ]
                    set type        [ lindex $L 0 ]
                    set action      [ lindex $L 1 ]
                    set_priority    [ lindex $L 2 ]
                    set target      [ lindex $L 3 ]
                    set parallelism [ lindex $L 4 ]
                    release $transform $type $action $target $parallelism
                }
            }

            reset_priority
            set msg [ adlb::get $WORK_TYPE(CONTROL) answer_rank ]
            if { [ string length $msg ] } {
                control $msg $answer_rank
            } else break
            debug "heap size: [ bytes [ c_utils::heapsize ] ]"
        }
    }

    # Release a work unit for execution here or elsewhere
    proc release { transform action_type action target parallelism } {

        global WORK_TYPE

        debug "release: \{$transform\}"

        switch $action_type {
            1 { # $turbine::LOCAL
                debug "executing local action: \{$transform\} $action"
                # TODO: Ensure target allows this
                eval $action
            }
            2 { # $turbine::CONTROL
                adlb::put $target $WORK_TYPE(CONTROL) \
                    "command priority: $turbine::priority $action" \
                    $turbine::priority 1
            }
            3 { # $turbine::WORK
                adlb::put $adlb::RANK_ANY $WORK_TYPE(WORK) \
                    "$transform $action" \
                    $turbine::priority $parallelism
            }
            default {
                error "unknown action type!"
            }
        }
    }

    # Handle a message coming into this rule engine
    proc control { msg answer_rank } {

        log "control: $msg"

        variable complete_rank
        set complete_rank $answer_rank

        set header [ lindex $msg 0 ]
        # show header
        switch $header {
            command {
                set command [ lrange $msg 1 end ]
                if { [ string equal [ lindex $command 0 ] \
                                    "priority:" ] } {
                    set_priority [ lindex $command 1 ]
                    set command [ lrange $command 2 end ]
                }
                eval $command
            }
            close {
                set id [ lindex $msg 1 ]
                turbine::c::close $id
            }
            rule {
                turbine::c::rule {*}[ lrange $msg 1 end ]
            }
            default {
                error "unknown control message: $msg"
            }
        }
    }
}
