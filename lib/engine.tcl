
namespace eval turbine {

    namespace export start

    proc start { rules } {

        set rank [ adlb::rank ]
        puts "rank: $rank"
        set amserver [ adlb::amserver ]
        if { $amserver == 1 } return

        variable engines
        if { $rank < $engines } {
            engine $rules
        } else {
            worker
        }
    }

    proc engine { rules } {

        global WORK_TYPE

        puts "engine"
        ::eval $rules

        turbine::c::push

        while {true} {

            set ready [ turbine::c::ready ]
            # ready list may be empty
            foreach {transform} $ready {
                set command [ turbine::c::executor $transform ]
                puts "submitting: $command"
                adlb::put $adlb::ANY $WORK_TYPE(WORK) "$transform $command"
            }

            set msg [ adlb::get $WORK_TYPE(CONTROL) answer_rank ]
            if { [ string length $msg ] } {
                control $msg
            } else break
        }
        puts "engine done"
    }

    # Handle a message coming into this rule engine
    proc control { msg } {

        puts "control: $msg"

        set header [ lindex $msg 0 ]
        puts "header: $header"
        switch $header {
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
        puts "worker"

        while { true } {
            # puts "get"
            set msg [ adlb::get $WORK_TYPE(WORK) answer_rank ]

            set rule_id [ lreplace $msg 1 end ]
            set command [ lreplace $msg 0 0 ]
            if { ! [ string length $command ] } {
                # puts "empty"
                break
            }

            do_work $rule_id $command
        }
        puts "worker done"
    }

    # Worker: do actual work, handle errors, report back when complete
    proc do_work { rule_id command } {

        global WORK_TYPE

        # puts "rule_id: $rule_id"
        # puts "work: $command"

        if { [ catch { turbine::eval $command } e ] } {
            puts "work unit error: "
            puts $e
            # puts "[dict get $e -errorinfo]"
            error "rule: transform failed in command: $command"
        }
        adlb::put $adlb::ANY $WORK_TYPE(CONTROL) "complete $rule_id"
    }
}
