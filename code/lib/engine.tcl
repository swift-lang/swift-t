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
    namespace import c::pop_or_break

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
                pop_or_break transform type action priority target parallelism
                set_priority $priority
                release $transform $type $action $target $parallelism
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
                    "priority_command $prio $action" \
                    $prio $parallelism
            }
            3 { # $turbine::WORK
                set prio [ get_priority ]
                put $target $WORK_TYPE(WORK) \
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

        set hdr_end [ string wordend $msg 0 ]
        set header [ string range $msg 0 [ expr $hdr_end - 1 ] ]
        set cmd_start [ expr $hdr_end + 1 ]
        set msg_len [ string length $msg ]
        # show header
        switch $header {
            command {
                eval [ string range $msg $cmd_start $msg_len ]
            }
            priority_command {
                set prio_end [ string wordend $msg $cmd_start ] 
                set prio [ string range $msg $cmd_start [ expr $prio_end - 1 ] ]
                set_priority $prio 
                eval [ string range $msg [ expr $prio_end + 1 ] $msg_len ]
            }
            close {
                # expect ID as first argument, then any subsequent
                # text is subscript
                set id_end [ string wordend $msg $cmd_start ]
                set id [ string range $msg $cmd_start [ expr $id_end - 1 ] ]
                if { $msg_len > $id_end } {
                  set sub [ string range $msg [ expr $id_end + 1 ] $msg_len ]
                  turbine::c::close $id $sub
                } else {
                  turbine::c::close $id
                }
            }
            rule {
                # Use list tokenisation rules
                turbine::c::rule {*}[ lrange $msg 1 end ]
            }
            default {
                error "unknown control message: $msg"
            }
        }
    }
}
