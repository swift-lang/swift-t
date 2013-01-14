
# Flex Turbine+ADLB with quick put/iget
# Nice to have for quick manual experiments

# usage: mpiexec -l -n 3 bin/turbine test/adlb-putget.tcl

package require turbine 0.0.1

enum WORK_TYPE { T }

if [ info exists env(ADLB_SERVERS) ] {
    set servers $env(ADLB_SERVERS)
} else {
    set servers ""
}
if { [ string length $servers ] == 0 } {
    set servers 1
}
adlb::init $servers [ array size WORK_TYPE ]

set amserver [ adlb::amserver ]

set put_count 4

if { $amserver == 0 } {

    set rank [ adlb::rank ]
    if { $rank == 0 } {
        puts "clock: [ clock seconds ]"
        for { set i 0 } { $i < $put_count } { incr i } {
            adlb::put $adlb::RANK_ANY $WORK_TYPE(T) "hello_$i" 0
        }
        puts "clock: [ clock seconds ]"
    }
    set igets_left 10
    while 1 {
        puts "clock: [ clock seconds ]"
        if { $igets_left > 0 } {
            set msg [ adlb::iget $WORK_TYPE(T) answer_rank ]
        } else {
            set msg [ adlb::get $WORK_TYPE(T) answer_rank ]
        }
        puts "msg: $msg"
        if { [ string equal $msg ""              ] } break
        if { [ string equal $msg "ADLB_SHUTDOWN" ] } break
        if { [ string equal $msg "ADLB_NOTHING" ] } {
            incr igets_left -1
        } else {
            set igets_left 10
        }

        after 100
    }
} else {
    adlb::server
}

puts "finalizing..."
adlb::finalize
puts OK
