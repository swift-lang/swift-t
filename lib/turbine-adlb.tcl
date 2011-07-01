
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

    puts "engine"
    eval $rules

    turbine_push

    while {true} {

        set ready [ turbine_ready ]
        if { ! [ string length $ready ] } break

        foreach {transform} $ready {
            set command [ turbine_executor $transform ]
            puts "submitting: $command"
            adlb_put $command
            turbine_complete $transform
        }
    }
    puts "no more work to submit!"
    exec sleep 1
    set msg [ adlb_get ]
    puts "engine msg: $msg"
}

proc turbine_adlb_worker { } {

    puts "worker"

    while { true } {
        puts "get"
        set msg [ adlb_get ]
        set command [ string trim $msg ]
        if { ! [ string length $command ] } {
            puts "empty"
            break
        }
        puts "work: $command"
        if { [ catch { turbine_eval $command } ] } {
            error "rule: $transform failed in command: $command"
        }
    }
    puts "worker done"
}
