
# Flex ADLB DHT with Turbine data

package require turbine 0.1

namespace import turbine::string_*

if [ info exists env(ADLB_SERVERS) ] {
    set servers $env(ADLB_SERVERS)
} else {
    set servers ""
}
if { [ string length $servers ] == 0 } {
    set servers 1
}
if [ info exists env(ADLB_ENGINES) ] {
    set engines $env(ADLB_ENGINES)
} else {
    set engines ""
}
if { [ string length $engines ] == 0 } {
    set engines 1
}

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
