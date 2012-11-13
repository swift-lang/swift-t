
# Flex ADLB/MPE in Tcl
# No real Turbine data flow here

# This may be used as a benchmark by setting
# TURBINE_TEST_PARAM_1 in the environment

package require turbine 0.0.1

namespace import turbine::string_*

turbine::defaults
turbine::init $engines $servers

if { [ info exists env(TURBINE_TEST_PARAM_1) ] } {
    set iterations $env(TURBINE_TEST_PARAM_1)
} else {
    set iterations 4
}

# Define MPE events
set L [ mpe::create_pair "test_mpe-3" ]
set event_start [ lindex $L 0 ]
set event_end   [ lindex $L 1 ]

if { ! [ adlb::amserver ] } {

    set rank [ adlb::rank ]
    puts "rank: $rank"
    set workers [ adlb::workers ]
    if { $rank == 0 } {
	puts "iterations: $iterations"
        puts "events $event_start $event_end"
    }
    puts "workers:    $workers"

    for { set i 1 } { $i <= $iterations } { incr i } {

        mpe::log $event_start "start-loop-body"
        set id [ expr $rank + $i * ($workers + 1) ]
        adlb::create $id $adlb::STRING 0
        adlb::store $id $adlb::STRING "message rank:$rank:$i"
        adlb::close $id

        # turbine::c::debug "get"
        set msg [ adlb::retrieve $id ]
        # turbine::c::debug "got: $msg"
        mpe::log $event_end "end-loop-body"
    }
} else {
    adlb::server
}

turbine::finalize
puts OK
