
namespace eval turbine {

    namespace export start

    proc start { args } {

	variable stats
	dict set stats tasks_run 0

        set rules [ lindex $args 0 ]
        if { [ llength $args ] > 1 } {
            set engine_startup [ lindex $args 1 ]
        } else {
            set engine_startup ""
        }

        if { [ catch { enter_mode $rules $engine_startup } e ] } {
            abort $e
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

        debug "TURBINE ENGINE..."
        ::eval $startup
        if { [ adlb::rank ] == 0 } {
            eval $rules
        }

	variable stats
	dict set stats tasks_released 0

        turbine::c::push

        while {true} {
            while {true} {
                # Do local work until we have none
                set ready [ turbine::c::ready ]
                if { [ llength $ready ] == 0 } break
                foreach {transform} $ready {
                    set action   [ turbine::c::action   $transform ]
                    set_priority [ turbine::c::priority $transform ]
                    release $transform $action
                }
            }

            reset_priority
            set msg [ adlb::get $WORK_TYPE(CONTROL) answer_rank ]
            if { [ string length $msg ] } {
                control $msg $answer_rank
            } else break
        }
    }

    # Release a work unit for execution here or elsewhere
    proc release { transform action } {

	variable stats
	# dict incr stats tasks_released

        global WORK_TYPE
        set type    [ lindex $action 0 ]
        set command [ lindex $action 1 ]

        switch $type {
            1 { # $turbine::LOCAL
                eval $command
            }
            2 { # $turbine::CONTROL
                adlb::put $adlb::RANK_ANY $WORK_TYPE(CONTROL) \
                    "command priority: $turbine::priority $command" \
                    $turbine::priority
            }
            3 { # $turbine::WORK
                adlb::put $adlb::RANK_ANY $WORK_TYPE(WORK) \
                    "$transform $command" $turbine::priority
            }
            default {
                error "unknown action type!"
            }
        }
    }

    # Handle a message coming into this rule engine
    proc control { msg answer_rank } {

        debug "control: $msg"

	variable stats
        variable complete_rank
        set complete_rank $answer_rank

        set header [ lindex $msg 0 ]
        # show header
        switch $header {
            command {
		# dict incr stats tasks_run
                set command [ lrange $msg 1 end ]
                if { [ string equal [ lindex $command 0 ] \
                                    "priority:" ] } {
                    set_priority [ lindex $command 1 ]
                    set command [ lrange $command 2 end ]
                }
                eval $command
            }
            complete {
                set id [ lindex $msg 1 ]
                turbine::c::complete $id
                # branch_complete $id
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
        debug "TURBINE WORKER..."

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

	variable stats
	# dict incr stats tasks_run

        debug "eval: $command"

        if { [ catch { eval $command } e ] } {
            puts "work unit error: "
            puts $e
            # puts "[dict get $e -errorinfo]"
            error "rule: transform failed in command: $command"
        }
        adlb::put $answer_rank $WORK_TYPE(CONTROL) \
            "complete $rule_id" $turbine::default_priority
    }
}
