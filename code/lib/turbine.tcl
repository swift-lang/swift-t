
# turbine.tcl
# Main control functions

package provide turbine 0.1

namespace eval turbine {

    namespace export init finalize

    # Number of Turbine engines
    variable engines
    # Number of Turbine workers
    variable workers
    # Number of ADLB servers
    variable servers

    # User function
    # param e Number of engines
    # param s Number of ADLB servers
    proc init { e s } {

        # Obtain process counts
        variable engines servers
        set engines $e
        set servers $s

        puts "init: $e $s"

        # Set up work types
        enum WORK_TYPE { WORK CONTROL }
        global WORK_TYPE
        set types [ array size WORK_TYPE ]
        adlb::init $servers $types

        set adlb_workers [ adlb::workers ]
        set workers [ expr $adlb_workers - $servers ]

        c::init
        argv_init
    }

    proc eval { command } {

        set command [ string trim $command ]
        set prefix "[ string range $command 0 2 ]"
        if { [ string equal $prefix "tf:" ] } {
            set proccall [ lrange $command 1 end ]
            debug "eval: $proccall"
            ::eval $proccall
        } else {
            debug "exec: $command"
            ::eval "exec $command"
        }
    }

    proc debug { msg } {
        c::debug $msg
    }

    proc finalize { } {
        debug "finalize"
        turbine::c::finalize
        adlb::finalize
    }
}
