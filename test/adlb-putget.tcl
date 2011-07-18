
# Flex Turbine+ADLB with quick put/get
# Nice to have for quick manual experiments

# usage: mpiexec -l -n 3 bin/turbine test/adlb-putget.tcl

package require turbine 0.1

enum WORK_TYPE { T }

adlb::init [ array size WORK_TYPE ]
turbine::init

set amserver [ adlb::amserver ]

if { $amserver == 0 } {

    set rank [ adlb::rank ]
    if { $rank == 0 } {
        adlb::put $ADLB_ANY $WORK_TYPE(T) "hello"
    } else {
        set msg [ adlb::get $WORK_TYPE(T) answer_rank ]
        puts "answer_rank: $answer_rank"
        puts "msg: $msg"
    }
} else {
    puts "ADLB server exited!"
}

turbine::finalize
adlb::finalize
puts OK
