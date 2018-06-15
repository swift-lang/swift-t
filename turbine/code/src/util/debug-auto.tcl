# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

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

outln "#pragma once\n"

out "void turbine_debug_init(void);\n\n"

out "__attribute__ ((format (printf, 2, 3)))\n"
out "void turbine_debug(const char* token, " \
                       "const char* format, ...);\n\n"

out "void turbine_debug_finalize(void);\n\n"

# stdbool.h needed for bool type
outln "#include <stdbool.h>"
outln "#ifndef NDEBUG"
outln "extern bool turbine_debug_enabled;"
outln "#else"
outln "// compile-time constant for NDEBUG"
outln "static const bool turbine_debug_enabled = false;"
outln "#endif"

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

close $fd
