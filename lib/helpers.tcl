
# Shutdown TCL if condition does not hold
proc assert { condition msg } {
    if [ expr ! $condition ] {
        puts msg
        exit 1
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
