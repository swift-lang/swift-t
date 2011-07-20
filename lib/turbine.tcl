
# Turbine TCL library

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
    proc init { e } {

        enum WORK_TYPE { WORK CONTROL }
        global WORK_TYPE
        adlb::init 2
         # [ array size WORK_TYPE ]

        ::turbine::c::init
        variable engines servers
        set engines $e
        set servers [ adlb::servers ]
        set adlb_workers [ adlb::workers ]
        set workers [ expr $adlb_workers - $servers ]
        argv_init
    }

    proc eval { command } {

        set command [ string trim $command ]
        set prefix "[ string range $command 0 2 ]"
        puts "eval: $command"
        if { [ string equal $prefix "tp:" ] } {
            set proccall [ lrange $command 1 end ]
            puts "eval: $proccall"
            ::eval $proccall
        } else {
            puts "exec: $command"
            ::eval "exec $command"
        }
    }

    proc finalize { } {
        turbine::c::finalize
        adlb::finalize
    }
}
