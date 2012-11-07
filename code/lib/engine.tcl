
namespace eval turbine {

    namespace export start

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
                    set type     [ lindex $L 0 ]
                    set action   [ lindex $L 1 ]
                    set_priority [ lindex $L 2 ]
                    release $transform $type $action
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
    proc release { transform action_type action } {

        global WORK_TYPE

        debug "release: $transform"

        switch $action_type {
            1 { # $turbine::LOCAL
                debug "executing local action: $transform $action"
                eval $action
            }
            2 { # $turbine::CONTROL
                adlb::put $adlb::RANK_ANY $WORK_TYPE(CONTROL) \
                    "command priority: $turbine::priority $action" \
                    $turbine::priority
            }
            3 { # $turbine::WORK
                adlb::put $adlb::RANK_ANY $WORK_TYPE(WORK) \
                    "$transform $action" $turbine::priority
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
            default {
                error "unknown control message: $msg"
            }
        }
    }

    # Main worker loop
    proc worker { } {

        global WORK_TYPE

        while { true } {
            set msg [ adlb::get $WORK_TYPE(WORK) answer_rank ]

            set rule_id [ lreplace $msg 1 end ]
            set command [ lreplace $msg 0 0 ]
            if { ! [ string length $command ] } {
                # puts "empty"
                break
            }

            do_work $answer_rank $rule_id $command
        }
    }

    # Worker: do actual work, handle errors, report back when complete
    proc do_work { answer_rank rule_id command } {

        global WORK_TYPE

        debug "rule_id: $rule_id"
        debug "work: $command"
        debug "eval: $command"

        if { [ catch { eval $command } e ] } {
            puts "work unit error: "
            puts $e
            # puts "[dict get $e -errorinfo]"
            error "rule: transform failed in command: $command"
        }
    }
}
