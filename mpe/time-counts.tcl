#!/usr/bin/env tclsh

# Counts cumulative events completions over time

# Usage:
# time-counts.tcl <LOG> <MPI RANK>
# Environment must contain MPE_EVENTS

# the log is the MPE-generated CLOG2 file
# the MPI rank is the rank to be analyzed
# If MPI rank is -1, analyze all ranks

# MPE_EVENTS contains the space-separated list of event stops
# that you want to count
# E.g.: export MPE_EVENTS="ADLB_Put ADLB_Get"

# Settings:
# Bucket width in seconds
set bucket_width 0.0001

# Set up script
set script [ info script ]
set script_dir [ file dirname $script ]
set script_file [ file tail $script ]
source $script_dir/turbine.tcl

# Section I:   Basic definitions, setup
# Section II:  Populate IDs with known event type lines (type=sdef)
# Section III: Read real event lines (type=bare)
# Section IV:  Put events in buckets
# Section V:   Output buckets
#
# Sections II & III are separate (two passes through log)
# because event IDs may occur before they are defined

# SECTION I: SETUP

# The log we want to read
set input_log [ lindex $argv 0 ]
if { [ string length $input_log ] == 0 } {
    puts "Not given: input log (in clog format)"
    exit 1
}

# The rank we want to profile - may be (-1=all)
set mpi_rank [ lindex $argv 1 ]
if { [ string length $mpi_rank ] == 0 } {
    puts "Not given: MPI rank"
    exit 1
}

if { ! [ info exists env(MPE_EVENTS) ] } {
    puts "Not set: MPE_EVENTS"
    exit 1
}

verbose "$script_file: input log: $input_log"
verbose "$script_file: MPI rank: $mpi_rank"
verbose "$script_file: MPE_EVENTS: $env(MPE_EVENTS)"

if { ! [ file readable $input_log ] } {
    puts "File not found: $input_log"
    exit 1
}

# Time of last call to ADLB_Finalize
set finalize_time 0

set index 0
set time_previous 0
set completions 0

# Map from index to [ list timestamp completions ]
set timeline($index) [ list $time_previous $completions ]
incr index

# SECTION II: READ DEFINITIONS

read_defns $input_log

# SECTION III: READ EVENTS

# Read output of clog2_print on input_log
set fd [ open "|clog2_print $input_log" ]

set state_list $env(MPE_EVENTS)

proc process_line { line } {
    global state_list
    global ANY START STOP OFF ON
    global world mpi_rank finalize_time
    global IDs mode started
    global timeline index time_previous completions

    set type ""

    return


    # Parse the log line, creating local variables
    eval_kv $line


    # Is this a normal event line?
    if { ! [ string equal $type bare ] } {
        return
    }



    # Actual event line
    if { $mpi_rank == $ANY ||
         $mpi_rank == $rank } {

        # Convert event number back to event name
        set name [ dict get $IDs $et ]
        if { [ string equal $name "ADLB_Finalize" ] } {
            set finalize_time $ts
        }

        # Is this event name in the state_list?
        set i [ lsearch $state_list $name ]
        if { $i == -1 } return

        assert { expr $ts >= $time_previous }
        set time_previous $ts

        set m [ dict get $mode $et ]
        if { $m != $STOP } return
        incr completions
        set timeline($index) [ list $ts $completions ]
        incr index
    }
}

# Process lines other than type=sdef
set count 1
while { [ gets $fd line ] >= 0 } {
    if [ catch { process_line $line } e ] {
        puts "error in line $count: $line"
        puts $errorInfo
        exit
    }
}
close $fd

# SECTION V: OUTPUT


# Timeline index
set i 0
for { set i 0 } { $i < $index } { incr i } {

    set t [ lindex $timeline($i) 0 ]
    set c [ lindex $timeline($i) 1 ]
    puts "$t $c"
}
