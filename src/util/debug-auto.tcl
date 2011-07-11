
# This script auto-generates debug.h from debug-tokens.tcl
# For each token in debug-tokens.tcl, this generates
# a macro that is either a debugging printf call or void.

set script [ info script ]

set dir [ file dirname $script ]

source "$dir/debug-tokens.tcl"

set fd [ open "$dir/debug.h" w ]

array set tokens $INPUT

proc out { args } {
    global fd
    foreach arg $args {
        puts -nonewline $fd $arg
    }
}

out "\n"
out "// Header created by debug-auto.tcl at: [exec date]\n\n"

out "#ifndef DEBUG_H\n"
out "#define DEBUG_H\n\n"

out "void turbine_debug(char* token, char* format, ...);\n\n"

# String breaks were necessary to prevent TCL string interpolation
foreach token [ array names tokens ] {
    set symbol "ENABLE_DEBUG_$token"
    set macro "DEBUG_$token"
    if { $tokens($token) eq "ON" } {
        out "#define $symbol\n"
        out "#define $macro" "(format, args...) \\\n"
        out "\t turbine_debug(\"$macro\", format, ## args)\n"
    } else {
        out "#define $macro" "(format, args...)\n"
    }
    out "\n"
}

out "\n"
out "#endif\n"

close $fd
