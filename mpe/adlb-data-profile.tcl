#!/usr/bin/env tclsh

# Usage:
# adlb-data-profile.tcl <LOG> <MPI RANK>
# the log is the MPE-generated CLOG2 file
# the MPI rank is the rank to be analyzed
# If MPI rank is -1, analyze all ranks

# Settings:
# If 1, trace individual event times
set trace_calls 0
# If 1, be verbose
set verbose 0

# Set up script
set script [ info script ]
set script_dir [ file dirname $script ]
set script_file [ file tail $script ]
source $script_dir/turbine.tcl

set ignores {}
if [ info exists env(MPE_IGNORES) ] {
    set ignores $env(MPE_IGNORES)
}

# Section I: Basic definitions, setup
# Section II: Populate IDs with known event type lines (type=sdef)
# Section III: Read real event lines (type=bare)
# Section IV: Output event statistics
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

verbose "$script: input log: $input_log"
verbose "$script: MPI rank: $mpi_rank"

if { ! [ file readable $input_log ] } {
    puts "File not found: $input_log"
    exit 1
}

proc verbose { msg } {
    global verbose
    if { $verbose } {
        puts $msg
    }
}

# For each term "k1=v1" in given line, set Tcl variable $k1 to "v1"
# in caller's stack frame
# proc eval_kv { line } {
#     # First, fix lines with spaces:
#     set result [ regexp "(.*) = (.*)" $line x key value ]
#     if { $result } {
#         set line "$key=$value"
#     }
#     foreach term $line {
#         # puts "term: $term"
#         # if [ string equal $term "=" ] continue
#         set result [ regexp (.*)=(.*) $term x key value ]
#         # puts $result
#         if { $result } {
#             upvar 1 $key k
#             set k $value
#         }
#     }
# }

# Map from MPE event name + rank to previous start time
set started [ dict create ]

# Map from MPE event name to amount of time spent in that state
set profiles [ dict create ]

# Map from MPE event name to number of start occurrences
set calls [ dict create ]

# Time of last call to ADLB_Finalize
set finalize_time 0

# SECTION II: READ DEFINITIONS

read_defns $input_log

# Set up statistics for each event name
dict for { id name } $IDs {
    set m [ dict get $mode $id ]
    if { $m == $STOP } continue
    for { set rank 0 } { $rank < $world } { incr rank } {
        dict set started $name:$rank 0
        dict set calls   $name 0
        # verbose "started: $name:$rank -> 0"
    }
    dict set profiles $name 0
}

# SECTION III: READ EVENTS

# Read output of clog2_print on input_log
set fd [ open "|clog2_print $input_log" ]

proc process_line { line } {
    global trace_calls
    global ANY START STOP OFF ON
    global world mpi_rank finalize_time
    global IDs mode progress started profiles calls
    global ignores

    set ts ""

    # Parse the log line, creating local variables
    eval_kv $line

    # Is this a normal line?
    if { ! [ string length $ts ] } {
        return
    }
    if { ! [ string equal $type bare ] } {
        return
    }

    # Actual event line
    if { $mpi_rank == $ANY ||
         $mpi_rank == $rank } {
        set name [ dict get $IDs $et ]

        if { [ lsearch $ignores $name ] >= 0 } return

        set m [ dict get $mode $et ]
        if { $m == $START } {
            set last_start [ dict get $started $name:$rank ]
            if { $last_start != 0 } {
                error "$name: last start is not 0!"
            }
            dict incr calls $name
            # Set start time:
            dict set started $name:$rank $ts
            if { [ string equal $name "ADLB_Finalize" ] } {
                set finalize_time $ts
            }
        }
        if { $m == $STOP } {
            set last_start [ dict get $started $name:$rank ]
            if { $last_start == 0 } {
                error "$name: last start is 0!"
            }
            set t [ expr $ts - $last_start ]
            if { $trace_calls } {
                puts "$name: $t"
            }
            # Increment profile time:
            set p [ dict get $profiles $name ]
            set p [ expr $p + $t ]
            dict set profiles $name $p
            # Reset start time:
            dict set started $name:$rank 0
        }
    }
}

# Process lines other than type=sdef
set count 1
while { [ gets $fd line ] >= 0 } {
    if [ catch { process_line $line } e ] {
        puts "error in line $count: $line"
        puts $errorInfo
        exit 1
    }
}
close $fd

# SECTION IV: OUTPUT

# Report results
puts ""
puts "PROCS: $world"
puts "WALLTIME: [ format %0.4f $finalize_time ]"
set cpu_time [ format %0.4f [ expr $finalize_time * $world ] ]
puts "CPU_TIME: $cpu_time"
dict for { name p } $profiles {
    if { $p != 0 } {
        set c [ dict get $calls $name ]
        set r [ expr 100 * $p / $finalize_time / $world ]
        puts -nonewline "TOTAL: "
        puts -nonewline [ format %-15s $name ]
        puts -nonewline [ format %10i   $c ]
        puts -nonewline " "
        puts -nonewline [ format %0.4f $p ]
        puts            " ([ format %.4f $r ]%)"
    }
}
