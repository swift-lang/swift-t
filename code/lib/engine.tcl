
namespace eval turbine {

    namespace export start

    proc start { rules } {

        variable mode
        set rank [ adlb::rank ]
        set amserver [ adlb::amserver ]
        if { $amserver == 1 } {
	    set mode SERVER
	    return
	}

	variable stats
	dict set stats tasks_run 0

        variable engines
        if { $rank < $engines } {
	    set mode ENGINE
            engine $rank $rules
        } else {
	    set mode WORKER
            worker
        }
    }

    proc engine { rank rules } {

        global WORK_TYPE

        debug "TURBINE ENGINE..."
        if { $rank == 0 } {
            ::eval $rules
        }

	variable stats
	dict set stats tasks_released 0

        turbine::c::push

        while {true} {
            set ready [ turbine::c::ready ]
            # ready list may be empty
            foreach {transform} $ready {
                set command [ turbine::c::executor $transform ]
                release $transform $command
            }

            set msg [ adlb::get $WORK_TYPE(CONTROL) answer_rank ]
            if { [ string length $msg ] } {
                control $msg
            } else break
        }
    }

    # Release a work unit for execution elsewhere
    proc release { transform command } {

	variable stats
	dict incr stats tasks_released

        global WORK_TYPE
        set command [ string trim $command ]
        set prefix "[ string range $command 0 2 ]"
        if { [ string equal $prefix "tp:" ] } {
            set proccall [ lrange $command 1 end ]
            adlb::put $adlb::ANY $WORK_TYPE(CONTROL) \
                "procedure $command"
        } else {
            adlb::put $adlb::ANY $WORK_TYPE(WORK) \
                "$transform $command"
        }
    }

    # Handle a message coming into this rule engine
    proc control { msg } {

        debug "control: $msg"

	variable stats
        set header [ lindex $msg 0 ]
        show header
        switch $header {
            procedure {
		dict incr stats tasks_run
                set command [ lrange $msg 2 end ]
                ::eval $command
            }
            complete {
                set id [ lindex $msg 1 ]
                turbine::c::complete $id
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
            # puts "get"
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
	dict incr stats tasks_run

        if { [ catch { turbine::eval $command } e ] } {
            puts "work unit error: "
            puts $e
            # puts "[dict get $e -errorinfo]"
            error "rule: transform failed in command: $command"
        }
        adlb::put $answer_rank $WORK_TYPE(CONTROL) "complete $rule_id"
    }
}
