
# Flex Turbine+ADLB for steals with imbalanced task loads

# usage: bin/turbine -l -n 6 tests/adlb-steal-2.tcl

# Test will add tasks up until time exceeds DURATION
# Task run times are from 0 to max_task
# When a worker completes a task, it may submit new tasks

package require turbine 0.0.1

# Initial puts to bootstrap
set task_count_initial 5

# Maximal task length (seconds)
set task_length_max 5

# Probability of releasing new work
set task_chance 0.5

# Maximal number of new tasks found
set task_count_max 4

# Seed for srand().  rank is added before using
set rand_seed 1

enum WORK_TYPE { T }

if [ info exists env(ADLB_SERVERS) ] {
    set servers $env(ADLB_SERVERS)
} else {
    set servers ""
}
if { [ string length $servers ] == 0 } {
    set servers 2
}

# duration in seconds
if [ info exists env(TEST_DURATION) ] {
    set duration $env(TEST_DURATION)
} else {
    set duration 6
}

# Walltime since this script started
proc clock_wt { } {
    global clock_start
    set t [ clock seconds ]
    set d [ expr $t - $clock_start ]
    return $d
}

proc clock_report { } {
    set d [ clock_wt ]
    puts "clock: $d"
}

proc clock_fmt { } {
    return format "%4i" [clock_wt]
}

proc log { msg } {
    puts "LOG: [clock_fmt] $msg"
}

# Obtain a random task length
proc random_task { } {
    global task_length_max
    return [ expr round(rand() * $task_length_max) ]
}

# Return count of new work units
# Based on probability of work unit finding additional work
#  and
proc found_work { } {
    global duration
    global task_chance
    global task_count_max
    set t [ clock_wt ]
    if { $t < $duration } {
        if { rand() < $task_chance } {
            return $task_count_max
        }
    }
    return 0
}

proc put_found_work { } {
    global WORK_TYPE
    set new_tasks [ found_work ]
    log "new tasks: $new_tasks"
    for { set i 0 } { $i < $new_tasks } { incr i } {
        adlb::put $adlb::RANK_ANY $WORK_TYPE(T) [random_task] 0
    }
}

adlb::init $servers [ array size WORK_TYPE ]

set clock_start [ clock seconds ]

set amserver [ adlb::amserver ]

set rank [ adlb::rank ]

expr srand($rand_seed + $rank)

if { $amserver == 0 } {
    if { $rank == 0 } {
        for { set i 0 } { $i < $task_count_initial } { incr i } {
            clock_report
            adlb::put $adlb::RANK_ANY $WORK_TYPE(T) [random_task] 0
        }
    }
    after 3000
    log "worker starting"
    while 1 {
        clock_report
        set msg [ adlb::get $WORK_TYPE(T) answer_rank ]
        log "msg: '$msg'"
        if { [ string length $msg ] == 0 } break
        # log "answer_rank: $answer_rank"
        # Emulate work time
        log "work unit start"
        after [ expr $msg * 1000 ]
        log "work unit stop"
        put_found_work
    }
} else {
    adlb::server
}

clock_report
adlb::finalize
puts OK

proc exit args {}
