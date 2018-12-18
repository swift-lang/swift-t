#!/usr/bin/env tclsh

# Produce worker load level over time

# Usage:
# worker-load.tcl <LOG> <MPI RANK>

# the log is the MPE-generated CLOG2 file
# the MPI rank is the rank to be analyzed
# If MPI rank is -1, analyze all ranks

# Bucket width in seconds
set bucket_width 1

# Constants:
set this_script "worker-load"
set ANY -1

# Section I:   Basic definitions, setup
# Section II:  Populate IDs with known event type lines (type=sdef)
# Section III: Read real event lines (type=bare)
# Section IV:  Put events in buckets and emit loads
#
# Sections II & III are separate (two passes through log)
# because event IDs may occur before they are defined

# SECTION I: SETUP

# Set up script
set script [ info script ]
set script_dir [ file dirname $script ]
set script_file [ file tail $script ]
source $script_dir/turbine.tcl

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

verbose "$this_script: input log: $input_log"
verbose "$this_script: MPI rank: $mpi_rank"

if { ! [ file readable $input_log ] } {
    puts "File not found: $input_log"
    exit 1
}

# Time of last call to ADLB_Finalize
set finalize_time 0

# Map from time to [ list rank $START/$STOP ] pair
set timeline [ dict create ]

# Map from rank to component type: $ENGINE, $WORKER, $SERVER
array set component {}

# SECTION II: READ DEFINITIONS

read_defns $input_log

for { set i 0 } { $i < $world } { incr i } {
    # Treat all ranks as servers until we prove otherwise
    set component($i) $SERVER
}

# SECTION III: READ EVENTS

# Read output of clog2_print on input_log
set fd [ open "|clog2_print $input_log" ]

# The list of states that we are interested in
# Turbine work unit types: WORK=0, CONTROL=1
set state_list [ list user_state_0 ]

proc process_line { line } {
    global state_list component timeline
    global ANY START STOP OFF ON ENGINE WORKER SERVER
    global world mpi_rank finalize_time
    global IDs mode

    set type ""

    # Parse the log line, creating local variables
    eval_kv $line

    # Is this a normal event line?
    if { ! [ string equal $type bare ] } {
        return
    }

    # Actual event line
    if { $mpi_rank == $ANY ||
         $mpi_rank == $rank } {
        set name [ dict get $IDs $et ]
        if { [ string equal $name "ADLB_Finalize" ] } {
            set finalize_time $ts
        } elseif { [ string equal $name "user_state_0" ] } {
            set component($rank) $WORKER
        } else {
            if { $component($rank) == $SERVER } {
                set component($rank) $ENGINE
            }
        }

        set i [ lsearch $state_list $name ]
        if { $i == -1 } return

        # m is START or STOP
        set m [ dict get $mode $et ]
        dict set timeline $ts [ list $rank $m ]
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

# SECTION IV: Check loads

# Total number of events

set times  [ dict keys $timeline ]
set times  [ lsort -real $times ]
set total  [ llength $times ]
# Events of interest during the loop are at t in [t0,t1)
set t0 0
set t1 $bucket_width
# State is 0 (idle=$STOP) or 1 (busy=$START)
array set state {}
# Number of workers
set workers 0
for { set r 0 } { $r < $world } { incr r } {
    if { $component($r) == $WORKER } {
        set state($r) 0
        incr workers
    }
}

verbose "total: $total"
verbose "workers: $workers"
puts "0 0"
for { set i 0 } { $i < $total } { incr i } {
    set t [ lindex $times $i ]
    if { $t >= $t0 && $t < $t1 } {
        set event [ dict get $timeline $t ]
        set r [ lindex $event 0 ]
        set s [ lindex $event 1 ]
        # puts "state: $t $r $s"
        set state($r) $s
        continue
    } else {
        # Try this t again next time
        incr i -1
    }
    set load 0
    foreach { r s } [ array get state ] {
        incr load $s
    }
    # Output: time interval end, load
    # puts "$t1 $load"
    # Output: time interval end, load fraction
    set f [ expr $load / $workers ]
    set s [ format "%.5f" $f ]
    puts "$t1 $s"

    set t0 $t1
    set t1 [ expr $t1 + $bucket_width ]
}
puts "$t1 0"
