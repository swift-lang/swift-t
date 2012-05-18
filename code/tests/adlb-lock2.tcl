
# Flex ADLB data locks
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
    set iterations 1
}

if { ! [ adlb::amserver ] } {

    set rank [ adlb::rank ]
    puts "rank: $rank"

    set id 1001

    if { $rank == 0 } {
        adlb::create $id $adlb::STRING
        adlb::store $id $adlb::STRING "test-message"
        adlb::close $id
    } else {
        puts "waiting..."
        after 100
    }

    for { set i 1 } { $i <= $iterations } { incr i } {
        set b [ adlb::lock $id ]
        puts "lock: $id => $b"
        if { $b } {
            set msg [ adlb::retrieve $id ]
            after 100
            adlb::unlock $id
        } else {
            after 100
        }
    }
} else {
    adlb::server
}

turbine::finalize

puts OK
