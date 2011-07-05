
package require turbine 0.1

enum WORK_TYPE { CMDLINE }

set rc [ adlb_init [ array size WORK_TYPE ] ]
assert [ expr $rc == $ADLB_SUCCESS ] "Failed: adlb_init"

set amserver [ adlb_amserver ]

proc do_work {} {

    global WORK_TYPE
    while { true } {
        set work [ adlb_get $WORK_TYPE(CMDLINE) answer_rank ]
        if { [ string length $work ] } {
            puts "work: $work"
            eval exec $work
        } else {
            break
        }
    }
}

proc do_client {argc argv} {
    global ADLB_ANY
    global WORK_TYPE
    set rank [ adlb_rank ]

    if { $rank == 0 } {
        if { $argc != 1 } {
            puts "Need file argument!"
            exit 1
        }

        set batchfile [ lindex $argv 0 ]

        set fd [ open $batchfile r ]

        while { true } {
            gets $fd line
            set line [ string trimright $line ]
            if { [ string length $line ] } {
                adlb_put $ADLB_ANY $WORK_TYPE(CMDLINE) $line
            }
            if { [ eof $fd ] } {
                close $fd
                break
            }
        }
    }

    do_work
}

if { $amserver == 0 } {
    puts "ADLB_SUCCESS: $ADLB_SUCCESS"
    do_client $argc $argv
}

adlb_finalize

# Help TCL free memory
proc exit args {}
