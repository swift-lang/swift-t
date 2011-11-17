
# turbine.tcl
# Main control functions

package provide turbine 0.1

namespace eval turbine {

    namespace export init finalize

    # Mode is ENGINE, WORKER, or SERVER
    variable mode
    # Number of Turbine engines
    variable engines
    # Number of Turbine workers
    variable workers
    # Number of ADLB servers
    variable servers
    # Statistics
    variable stats

    # User function
    # param e Number of engines
    # param s Number of ADLB servers
    proc init { e s } {

	variable engines
	variable servers
        set engines $e
        set servers $s

        # Set up work types
        enum WORK_TYPE { WORK CONTROL }
        global WORK_TYPE
        set types [ array size WORK_TYPE ]
        adlb::init $servers $types
        c::init [ adlb::amserver ]

        start_stats

	if { [ adlb::amserver ] == 1 } {
            adlb::server
            return
        }

        set adlb_workers [ adlb::workers ]
        set workers [ expr $adlb_workers - $servers ]

        argv_init
    }

    proc start_stats { } {

	variable stats
	set stats [ dict create ]

        adlb::barrier
        c::normalize
        c::log "starting clock"
	dict set stats clock_start [ clock clicks -milliseconds ]
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

    proc report_stats { } {

	variable mode
	variable engines
	variable stats
	# puts "engines: $engines"
	set start [ dict get $stats clock_start ]
	set stop [ clock clicks -milliseconds ]
	set stats [ dict remove $stats clock_start ]
        # duration in milliseconds
        set duration [ expr $stop - $start ]
        # walltime in seconds
        set w [ expr $duration / 1000.0 ]
        set walltime [ format "%0.3f" $w ]
	dict set stats walltime $walltime

	dict for { key value } $stats {
	    puts "STATS: $key $value"
	}
    }

    proc finalize { } {
        debug "finalize"
	report_stats
        turbine::c::finalize
        adlb::finalize
    }

    # Set engines and servers in the caller's stack frame
    # Used to get tests running, etc.
    proc defaults { } {

        global env
        upvar 1 engines e
        upvar 1 servers s

        if [ info exists env(TURBINE_ENGINES) ] {
            set e $env(TURBINE_ENGINES)
        } else {
            set e ""
        }
        if { [ string length $e ] == 0 } {
            set e 1
        }

        if [ info exists env(ADLB_SERVERS) ] {
            set s $env(ADLB_SERVERS)
        } else {
            set s ""
        }
        if { [ string length $s ] == 0 } {
            set s 1
        }
    }
}
