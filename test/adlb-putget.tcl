
# Flex Turbine+ADLB with quick put/get
# Nice to have for quick manual experiments

# usage: mpiexec -l -n 3 bin/turbine test/adlb-putget.tcl

package require turbine 0.1
adlb_init
turbine_init

set amserver [ adlb_amserver ]

if { $amserver == 0 } {

    set rank [ adlb_rank ]
    if { $rank == 0 } {
        adlb_put $ADLB_ANY "hello"
    } else {
        set msg [ adlb_get answer_rank ]
        puts "answer_rank: $answer_rank"
        puts "msg: $msg"
    }
} else {
    puts "ADLB server exited!"
}

turbine_finalize
adlb_finalize
puts OK
