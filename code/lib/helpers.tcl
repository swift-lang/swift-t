
# Shutdown TCL if condition does not hold
proc assert { condition msg } {
    if [ expr ! $condition ] {
        puts msg
        exit 1
    }
}

# Assert that each x is non-empty
# Note: each x should be passed as a symbol
#       this is so we can print a nice error message
proc nonempty { args } {
    foreach x $args {
        upvar 1 $x v
        if { ( ! [ info exists v ] ) ||
             [ string length $v ] == 0 } {
            error "This must not be empty: $x"
        }
    }
}

# assert that each x is an empty list
# Note: each x should be passed as a symbol
#       this is so we can print a nice error message
proc empty { args } {
    foreach x $args {
        upvar 1 $x v
        if { ( ! [ info exists v ] ) ||
             [ string length $v ] > 0 } {
            error "This must be an empty list/string: $x"
        }
    }
}

# Given: enum E { T1 T2 }
# Defines global array E with members E(T1)=0, E(T2)=1
proc enum { name members } {
    uplevel #0 "global $name"
    global $name
    set n 0
    foreach m $members {
        set ${name}($m) $n
        incr n
    }
}

# Given: set d [ readfile file.txt ]
# Returns an array with $d(0) = line 1, $d(0) = line 2, etc.
# DOES NOT WORK
proc readfile { filename } {
    puts $filename
    set fd [ open $filename r ]
    set i 0
    while { [ gets $fd line ] >= 0 } {
        puts "$i $line"
         # array set $result { red a }
        incr i
    }
    return $result
}

# Debugging helper
proc show { v } {
    upvar $v t
    turbine::debug "$v: $t"
}
