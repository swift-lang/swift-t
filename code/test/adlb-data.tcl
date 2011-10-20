
# Flex ADLB DHT with Turbine data
# No real Turbine data flow here

package require turbine 0.1

namespace import turbine::string_*

turbine::defaults
turbine::init $engines $servers

if { ! [ adlb::amserver ] } {

    set count 4

    set rank [ adlb::rank ]
    puts "rank: $rank"
    set workers [ adlb::workers ]

    for { set i 0 } { $i < $count } { incr i } {
        set id [ expr $rank + $i * $workers + 1]
        string_init $id
        string_set $id "message rank:$rank:$i"

        set id [ expr $rank + $i * $workers + 1 ]
        puts "get"
        set msg [ string_get $id ]
        puts "got: $msg"
    }
}

turbine::finalize

puts OK
