
# Reusable Tcl tools

# If 1, be verbose
set verbose 0

if { [ info exists env(VERBOSE) ] &&
     $env(VERBOSE) == "1" } {
    set verbose 1
}

proc assert { args } {
    set expression [ lindex $args 0 ]
    set message    [ lindex $args 1 ]
    if { ! [ string length $message ] } {
        set message "ASSERT"
    }
    if { ! [ uplevel 1 $expression ] } {
        uplevel 1 "error $message"
    }
}

proc verbose { msg } {
    global verbose
    if { $verbose } {
        puts $msg
    }
}
