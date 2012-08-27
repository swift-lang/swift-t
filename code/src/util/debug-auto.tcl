
# This script auto-generates debug.h from debug-tokens.tcl
# For each token in debug-tokens.tcl, this generates
# a macro that is either a debugging printf call or void.

set script [ info script ]

set dir [ file dirname $script ]

source "$dir/debug-tokens.tcl"

set fd [ open "$dir/debug.h" w ]

array set tokens $INPUT

# Output functions take multiple arguments for complex strings
# String breaks were necessary to prevent Tcl string interpolation
#        of C preprocessor syntax
proc out { args } {
    global fd
    foreach arg $args {
        puts -nonewline $fd $arg
    }
}

proc outln { args } {
    global fd
    eval out $args
    puts $fd ""
}

outln
outln "// Header created by debug-auto.tcl at: [exec date]\n"

outln "#ifndef DEBUG_H"
outln "#define DEBUG_H\n"

out "void turbine_debug_init(void);\n\n"

out "__attribute__ ((format (printf, 2, 3)))\n"
out "void turbine_debug(const char* token, " \
                       "const char* format, ...);\n\n"

out "void turbine_debug_finalize(void);\n\n"

# Note that we provide a noop for NDEBUG
foreach token [ array names tokens ] {
    outln "// Macros for user token: $token\n"
    outln "#ifndef NDEBUG"
    set symbol "ENABLE_DEBUG_$token"
    set macro "DEBUG_$token"
    if { $tokens($token) eq "ON" } {
        outln "#define $symbol"
        outln "#define $macro" "(format, args...) \\"
        outln "\t turbine_debug(\"$macro\", format, ## args)"
    } else {
        outln "#define $macro" "(format, args...)"
    }
    outln "#else"
    outln "// noop for NDEBUG"
    outln "#define $macro" "(format, args...)"
    outln "#endif\n"
}

out "\n"
out "#endif\n"

close $fd
