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

# HELPERS.TCL
# Tcl helpers that do not refer to Turbine features

# Shutdown Tcl if condition does not hold
proc assert { condition msg } {
    if { ! [ uplevel 1 "expr $condition" ] } {
        puts $msg
        exit 1
    }
}

proc check { condition msg } {
    if { ! [ uplevel 1 "expr $condition" ] } {
        error $msg
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
            error "Var $x be an empty list/string.  Contents were \"$v\""
        }
    }
}

# Sometimes it is confusing when an error message reports blankness
proc something { x } {
    if { [ string length [ string trim $x ] ] == 0 } {
        return "(nothing)"
    }
    return $x
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
proc show { args } {
    foreach v $args {
        upvar $v t
        puts "$v: $t"
    }
}

set KB 1024
set MB [ expr {$KB * $KB} ]
set GB [ expr {$MB * $KB} ]

# Human readable byte messages
proc bytes { b } {
    global KB
    global MB
    global GB
    # 3 decimal digits
    set fmt "%.3f"
    if { $b < $KB } {
        return "$b B"
    } elseif { $b < $MB } {
        set kb [ expr {double($b) / $KB} ]
        set kbs [ format $fmt $kb ]
        return "$kbs KB"
    } elseif { $b < $GB } {
        set mb [ expr {double($b) / $MB} ]
        set mbs [ format $fmt $mb ]
        return "$mbs MB"
    }

    set gb [ expr {double($b) / $GB} ]
    set gbs [ format $fmt $gb ]
    return "$gbs GB"
}

# Pull one item out of this list at random
proc draw { L } {
    set n [ llength $L ]
    set i [ turbine::randint_impl 0 $n ]
    return [ lindex $L $i ]
}

# Tcl function
proc cat { args } {
    return [ join $args " " ]
}

namespace eval turbine {
  
  # Create a dictionary with integer keys numbered from start with contents
  # of list
  proc dict_from_list { l {start_index 0}} {
    set d [ dict create ]
    set i $start_index

    foreach x $l {
      dict append d $i $x
      incr i
    }

    return $d
  }

}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
