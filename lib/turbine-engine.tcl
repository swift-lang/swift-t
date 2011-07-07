
# Turbine TCL library

package provide turbine 0.1

proc turbine_engine { } {

    turbine_push

    while {true} {

        set ready [ turbine_ready ]
        if { ! [ string length $ready ] } break

        foreach {transform} $ready {
            set command [ turbine_executor $transform ]
            puts "executing: $command"
            if { [ catch { turbine_eval $command } ] } {
                error "rule: $transform failed in command: $command"
            }
            turbine_complete $transform
        }
    }
}
