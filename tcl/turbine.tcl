
# Turbine TCL library

proc turbine_engine { } {
    while {true} {

        turbine_push

        set ready [ turbine_ready ]
        if { ! [ string length $ready ] } break

        foreach {transform} $ready {
            set command [ turbine_executor $transform ]
            puts "executing: $command"
            if { [ catch { eval exec $command } ] } {
                error "rule: $transform failed in command: $command"
            }
            turbine_complete $transform
        }
    }
}
