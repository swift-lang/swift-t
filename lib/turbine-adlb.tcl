
proc turbine_adlb { } {

    set rank [ adlb_rank ]
    set amserver [ adlb_amserver ]
    if { $amserver == 1 } return
    if { $rank == 0 } {
        turbine_adlb_engine $rules
    }
    else {
        turbine_adlb_worker
    }
}

proc turbine_adlb_engine { rules } {

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
}

proc turbine_worker_adlb { } {

    while { true } {
        set msg [ adlb_get ]
        set command [ string trim $msg ]
        if { ! [ string length $command ] } break

        puts "executing: $command"
        if { [ catch { turbine_eval $command } ] } {
            error "rule: $transform failed in command: $command"
        }
    }
    puts "worker done"
}
