
# Turbine TCL library

package provide turbine 0.1

namespace eval turbine {

    proc eval { command } {

        set command [ string trim $command ]
        set prefix "[ string range $command 0 2 ]"
        # puts "eval: $command"
        if { [ string equal $prefix "tp:" ] } {
            set proccall [ lrange $command 1 end ]
            # puts "eval: $proccall"
            ::eval $proccall
        } else {
            # puts "exec: $command"
            ::eval "exec $command"
        }
    }
}
