
namespace eval turbine::adlb {

    namespace export init start finalize

    proc init { } {
        # WORK: TURBINE/ADLB work unit
        # DONE: Message for rule engine that work was completed
        enum WORK_TYPE { WORK DONE }
        global WORK_TYPE
        turbine::init
        adlb::init [ array size WORK_TYPE ]
    }

    proc start { rules } {

        set rank [ adlb::rank ]
        puts "rank: $rank"
        set amserver [ adlb::amserver ]
        if { $amserver == 1 } return

        if { $rank == 0 } {
            engine $rules
        } else {
            worker
        }
    }

    proc engine { rules } {

        global ADLB_ANY
        global WORK_TYPE

        puts "engine"
        eval $rules

        turbine::c::push

        while {true} {

            set ready [ turbine::c::ready ]
            # ready list may be empty
            foreach {transform} $ready {
                set command [ turbine::c::executor $transform ]
                # puts "submitting: $command"
                adlb::put $ADLB_ANY $WORK_TYPE(WORK) "$transform $command"
            }

            # exec sleep 1
            set msg [ adlb::get $WORK_TYPE(DONE) answer_rank ]
            # puts "engine msg: $msg"
            if { [ string length $msg ] } {
                turbine::c::complete $msg
            } else {
                break
            }
        }
    }

    proc worker { } {

        global ADLB_ANY
        global WORK_TYPE
        puts "worker: $ADLB_ANY"

        while { true } {
            # puts "get"
            set msg [ adlb::get $WORK_TYPE(WORK) answer_rank ]

            set rule_id [ lreplace $msg 1 end ]
            set command [ lreplace $msg 0 0 ]
            if { ! [ string length $command ] } {
                # puts "empty"
                break
            }
            # puts "rule_id: $rule_id"
            # puts "work: $command"

            if { [ catch { turbine::eval $command } ] } {
                error "rule: transform failed in command: $command"
            }
            adlb::put $ADLB_ANY $WORK_TYPE(DONE) $rule_id
        }
        puts "worker done"
    }

    proc finalize { } {
        turbine::finalize
        adlb::finalize
    }
}
