
# Turbine TCL library

package provide turbine 0.1

proc turbine_eval { command } {

    set command [ string trim $command ]
    set prefix "[ string range $command 0 2 ]"
    if { [ string equal $prefix "tp:" ] } {
        set proccall [ lrange $command 1 end ]
        puts "eval: $proccall"
        eval $proccall
    } else {
        puts "exec: $command"
        eval exec $command
    }
}

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
