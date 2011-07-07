
proc turbine_adlb_init { } {
    # WORK: TURBINE/ADLB work unit
    # DONE: Message for rule engine that work was completed
    enum WORK_TYPE { WORK DONE }
    global WORK_TYPE
    turbine_init
    adlb_init [ array size WORK_TYPE ]
}

proc turbine_adlb { rules } {

    set rank [ adlb_rank ]
    puts "rank: $rank"
    set amserver [ adlb_amserver ]
    if { $amserver == 1 } return

    if { $rank == 0 } {
        turbine_adlb_engine $rules
    } else {
        turbine_adlb_worker
    }
}

proc turbine_adlb_engine { rules } {

    global ADLB_ANY
    global WORK_TYPE

    puts "engine"
    eval $rules

    turbine_push

    while {true} {

        set ready [ turbine_ready ]
        # ready list may be empty
        foreach {transform} $ready {
            set command [ turbine_executor $transform ]
            puts "submitting: $command"
            adlb_put $ADLB_ANY $WORK_TYPE(WORK) "$transform $command"
        }

        # exec sleep 1
        set msg [ adlb_get $WORK_TYPE(DONE) answer_rank ]
        puts "engine msg: $msg"
        if { [ string length $msg ] } {
            turbine_complete $msg
        } else {
            break
        }
    }
}

proc turbine_adlb_worker { } {

    global ADLB_ANY
    global WORK_TYPE
    puts "worker: $ADLB_ANY"

    while { true } {
        puts "get"
        set msg [ adlb_get $WORK_TYPE(WORK) answer_rank ]

        set rule_id [ lreplace $msg 1 end ]
        set command [ lreplace $msg 0 0 ]
        if { ! [ string length $command ] } {
            puts "empty"
            break
        }
        puts "rule_id: $rule_id"
        puts "work: $command"

        if { [ catch { turbine_eval $command } ] } {
            error "rule: transform failed in command: $command"
        }
        adlb_put $ADLB_ANY $WORK_TYPE(DONE) $rule_id
    }
    puts "worker done"
}
