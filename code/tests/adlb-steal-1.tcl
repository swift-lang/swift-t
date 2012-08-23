
# Flex Turbine+ADLB for steals

# usage: bin/turbine -l -n 6 tests/adlb-steal-1.tcl

package require turbine 0.0.1

enum WORK_TYPE { T }

set start [ clock seconds ]

proc clock_report { } {
    global start
    set t [ clock seconds ]
    set d [ expr $t - $start ]
    puts "clock: $d"
}

if [ info exists env(ADLB_SERVERS) ] {
    set servers $env(ADLB_SERVERS)
} else {
    set servers 2
}

adlb::init $servers [ array size WORK_TYPE ]

set amserver [ adlb::amserver ]

set PUTS 10

if { $amserver == 0 } {

    set rank [ adlb::rank ]
    if { $rank == 0 } {
        for { set i 0 } { $i < $PUTS } { incr i } {
            adlb::put $adlb::RANK_ANY $WORK_TYPE(T) "wu-$i" 0
        }
    } else {
        after 5000
        while 1 {
            set msg [ adlb::get $WORK_TYPE(T) answer_rank ]
            puts "msg: '$msg'"
            if { [ string length $msg ] == 0 } break
            # puts "answer_rank: $answer_rank"
        }
    }
    puts "WORKER_DONE"
} else {
    adlb::server
}

adlb::finalize
puts OK

proc exit args {}
