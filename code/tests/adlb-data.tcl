
# Flex ADLB data store with Turbine data
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

if { ! [ adlb::amserver ] } {

    set rank [ adlb::rank ]
    puts "rank: $rank"
    set workers [ adlb::workers ]
    if { $rank == 0 } {
	puts "iterations: $iterations"
    }
        puts "workers:    $workers"

    for { set i 1 } { $i <= $iterations } { incr i } {
        set id [ expr $rank + $i * ($workers + 1) ]
        adlb::create $id $adlb::STRING 0
        adlb::store $id $adlb::STRING "message rank:$rank:$i"

        # turbine::c::debug "get"
        set msg [ adlb::retrieve $id ]
        # turbine::c::debug "got: $msg"
    }
} else {
    adlb::server
}

turbine::finalize

puts OK
