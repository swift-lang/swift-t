
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
