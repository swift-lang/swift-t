
# Flex ADLB DHT with Turbine data

package require turbine 0.1

turbine::adlb::init

namespace import turbine::adlb::data::*

set count 4
if { ! [ adlb::amserver ] } {
    set rank [ adlb::rank ]
    puts "rank: $rank"
    set workers [ adlb::workers ]

    for { set i 0 } { $i < $count } { incr i } {
        set id [ expr ( $rank + $i ) % $workers ]
        string_init $id
        string_set $id "msg rank:$rank"

        set id [ expr ( $rank + 1 + $i ) % $workers ]
        puts "get"
        set msg [ string_get $id ]
        puts "got: $msg"
    }
}

turbine::adlb::finalize

puts OK
