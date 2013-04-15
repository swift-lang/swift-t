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

    # Import adlb commands
    namespace import ::adlb::put ::adlb::get ::adlb::RANK_ANY

    # Import turbine c command
    namespace import c::ready c::pop

    proc engine { rules startup } {

        global WORK_TYPE

        set debug_enabled [ turbine::c::debug_on ]

        turbine::c::engine_init
        ::eval $startup
        if { [ adlb::rank ] == 0 } {
            eval $rules
        }

        turbine::c::push

        while {true} {
            while {true} {
                # Do local work until we have none
                set ready_transforms [ ready ]
                if { [ llength $ready_transforms ] == 0 } break
                foreach {transform} $ready_transforms {
                    pop $transform type action priority target parallelism
                    set_priority $priority
                    release $transform $type $action $target $parallelism
                }
            }

            reset_priority
            set msg [ get $WORK_TYPE(CONTROL) answer_rank ]
            if { [ string length $msg ] } {
                control $msg $answer_rank
            } else break

            if { $debug_enabled } {
              debug "heap size: [ bytes [ c_utils::heapsize ] ]"
            }
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
                set prio [ get_priority ]
                put $target $WORK_TYPE(CONTROL) \
                    "command priority: $prio $action" \
                    $prio $parallelism
            }
            3 { # $turbine::WORK
                set prio [ get_priority ]
                put $::adlb::RANK_ANY $WORK_TYPE(WORK) \
                    "$transform $action" $prio $parallelism
            }
            default {
                error "unknown action type!"
            }
        }
    }

    # Handle a message coming into this rule engine
    proc control { msg answer_rank } {

        log "control: $msg"

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
                debug "engine eval: $command"
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
