
package require turbine 0.1

# source helpers.tcl

set rc [ adlb_init ]
assert [ expr $rc == $ADLB_SUCCESS ] "Failed: adlb_init"

set amserver [ adlb_amserver ]

proc do_work {} {
    while { true } {
        set work [ adlb_get ]
        if { [ string length $work ] } {
            puts "work: $work"
            eval exec $work
        } else {
            break;
        }
    }
}

proc do_client {argc argv} {
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
                adlb_put $line
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
