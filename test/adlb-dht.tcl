
# Flex ADLB DHT

package require turbine 0.1
adlb_init 1
turbine_init

set count 4

if { ! [ adlb_amserver ] } {
    set rank [ adlb_rank ]
    puts "rank: $rank"
    set workers [ adlb_workers ]

    for { set i 0 } { $i < $count } { incr i } {
        set id [ expr ( $rank + $i ) % $workers ]
        adlb_store $id "msg rank:$rank"

        set id [ expr ( $rank + 1 + $i ) % $workers ]
        set msg [ adlb_retrieve $id ]
        puts "got: $msg"
    }
}

turbine_finalize
adlb_finalize
puts OK
