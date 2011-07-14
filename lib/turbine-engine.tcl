
# Turbine TCL library

package provide turbine 0.1

proc turbine_engine { } {

    turbine_debug "engine start..."

    turbine_push

    while {true} {

        set ready [ turbine_ready ]
        if { ! [ string length $ready ] } break

        foreach transform $ready {
            set command [ turbine_executor $transform ]
            turbine_debug "executing: $command"
            if { [ catch { turbine_eval $command } e v ] } {
                puts "[ dict get $v -errorinfo]"
                puts "\nrule $transform failed: $command\n"
                return false
            }
            turbine_complete $transform
        }
    }
    turbine_debug "engine stop"
    return true
}
