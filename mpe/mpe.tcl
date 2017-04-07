
# MPE.TCL

# Reusable MPE log processing functions

set mpe_script [ info script ]
set mpe_script_dir [ file dirname $mpe_script ]
set mpe_script_file [ file tail $mpe_script ]
source $mpe_script_dir/util.tcl

set ANY -1

# For each term "k1=v1" in given line, set Tcl variable $k1 to "v1"
# in caller's stack frame
# Useful for text from clog2_print
proc eval_kv { line } {
    # First, fix lines with spaces:
    set result [ regexp "(.*) = (.*)" $line x key value ]
    if { $result } {
        set line "$key=$value"
    }
    foreach term $line {
        # puts "term: $term"
        # if [ string equal $term "=" ] continue
        set result [ regexp (.*)=(.*) $term x key value ]
        # puts $result
        if { $result } {
            upvar 1 $key k
            set k $value
        }
    }
}

# CLOG will give us the max_comm_world_size
set world -1

# Map from start or stop event IDs to known MPE event names
set IDs [ dict create ]

# This is a start event
set START 1
# This is a stop event
set STOP 0

# Map from start, stop event IDs to START or STOP (event mode)
set mode [ dict create ]

proc process_defn { line } {

    global world
    global ANY START STOP OFF ON
    global IDs mode progress profiles calls

    set ts ""

    # Parse the log line, creating local variables
    eval_kv $line

    # Is this a parameter setting line?
    if { [ info exists max_comm_world_size ] } {
        set world $max_comm_world_size
        verbose "world: $world"
        return
    }

    # Is this a normal line?
    if { ! [ string length $ts ] } {
        return
    }

    if { ! [ string equal $type sdef ] } {
        return
    }

    # Define event IDs
    # Start Event Type
    dict set IDs $s_et $name
    dict set mode $s_et $START
    # End Event Type
    dict set IDs $e_et $name
    verbose "$s_et $e_et -> $name"
    dict set mode $e_et $STOP
    for { set rank 0 } { $rank < $world } { incr rank } {
        dict set started $name:$rank 0
        dict set calls   $name 0
        # verbose "started: $name:$rank -> 0"
    }
}

proc read_defns { input_log } {

    # Read output of clog2_print on input_log
    set fd [ open "|clog2_print $input_log" ]

    set count 1
    while { [ gets $fd line ] >= 0 } {
        if [ catch { process_defn $line } e ] {
            puts "error in line $count: $line"
            puts $errorInfo
            exit 1
        }
    }
    close $fd
}
