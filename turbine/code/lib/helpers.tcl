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
    show_token $v

    upvar $v t
    if { ! [ info exists v ] } {
      error "show: variable does not exist: $v"
    }
    # Make copy for possible modification:
    set s $t
    if { [ string length $s ] == 0 } { set s "''" }
    puts "$v: $s"
  }
}

proc showln { args } {
  foreach v $args {
    show_token $v

    # Actual variable
    upvar $v t
    if { ! [ info exists v ] } {
      error "show: variable does not exist: $v"
    }
    # Make copy for possible modification:
    set s $t
    if { [ string length $s ] == 0 } { set s "''" }
    puts -nonewline "$v=$s "
  }
  puts ""
}

proc show_token { v } {
  # Token
  if { [ string first "@" $v ] == 0 } {
    set s [ string range $v 1 end ]
    puts -nonewline "$s "
    return -code continue
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

proc cat { args } {
    return [ join $args " " ]
}

proc puts* { args } {
    puts [ join $args "" ]
}

proc puts** { args } {
  foreach a $args {
    if { [ string length $a ] == 0 } {
      puts -nonewline "'' "
    } else {
      puts -nonewline "$a "
    }
  }
  puts ""
}

proc putsn { args } {
    puts [ join $args "\n" ]
}

proc printf { fmt args } {
    puts [ format $fmt {*}$args ]
}

proc log* { args } {
  turbine::c::log [ join $args "" ]
}

proc log** { args } {
  set msg ""
  foreach a $args {
    if { [ string length $a ] == 0 } {
      append msg "'' "
    } else {
      append msg "$a "
    }
  }
  log* $msg
}

# Remove and return element 0 from list
proc list_pop_first { L_name } {
    upvar $L_name L
    set result [ lindex $L 0 ]
    set L [ lreplace $L 0 0 ]
    return $result
}

namespace eval turbine {

  # Create a dictionary with integer keys numbered from start with contents
  # of list
  proc list2dict { l {start_index 0}} {
    set d [ dict create ]
    set i $start_index

    foreach x $l {
      dict append d $i $x
      incr i
    }

    return $d
  }

}

# Split value into two parts around given token
# If token is not found, return [ list value "" ]
proc split_first { value token } {
  # p0 is first character of token in value
  set p0 [ string first $token $value ]
  if { $p0 == -1 } { return [ list $value "" ]}
  # p1 is just past last character of token in value
  set p1 [ expr $p0 + [ string length $token ] ]
  set r0 [ string range $value 0 [ expr $p0 - 1 ] ]
  set r1 [ string range $value $p1 end ]
  return [ list $r0 $r1 ]
}

# Ternary operator
proc ternary { condition a b } {
    if [ uplevel expr $condition ] {
        return $a
    } else {
        return $b
    }
}

# usage:   env [-f] -r key [dflt] (return)
# returns: value if key found else dflt, which defaults to {}
# usage:   env [-f] key value_name [dflt]
# returns: true if key found else false
# crashes if -f and key not found (force)
proc env { args  } {

  global env

  getopts A P "fr" $args

  set key $P(0)
  if [ info exists A(r) ] {
    if { [ array size P ] == 2 } {
      set dflt $P(1)
    } else {
      set dflt {}
    }
  } else {
    set value_name $P(1)
    upvar $value_name value
    set has_dflt [ expr [ array size P ] == 3 ]
    if $has_dflt { set dflt $P(2) }
  }

  if { ! [ info exists env($key) ] } {
    if [ info exists A(f) ] {
      puts "Set $key"
      exit 1
    } else {
      if [ info exists A(r) ] {
        return $dflt
      } else {
        if $has_dflt {
          set value $dflt
        }
        return false
      }
    }
  }

  # Key exists
  if [ info exists A(r) ] {
    return $env($key)
  } else {
    set value $env($key)
    return true
  }
}

# Assign argv to given names
# A: Associative-array: map option to value
# P: Positional parameters: indexed from 0
# opts: Options string e.g., "hc:p"
# V: e.g., $argv
proc getopts { A_name P_name opts V } {
    upvar $A_name A
    upvar $P_name P
    upvar optind count
    # Colons
    array set C {}
    _getopts_parse_opt_string C $opts
    set i 0
    set count 0
    set q 0
    set N [ llength $V ]
    set dash_found false
    while { $i < $N } {
        set t [ lindex $V $i ]
        set c [ string range $t 0 0 ]
        if { $c eq "-" && ! $dash_found} {
            set c [ string range $t 1 1 ]
        } else {
            set P($q) $t
            incr q
            incr i
            continue
        }
        if { $c eq "-" } { # Found --
            set dash_found true
            incr i
            incr count
            continue
        }
        if { ! [ info exists C($c) ] } {
            error "getopts: invalid flag: $c"
        }
        if { [ string equal $C($c) ":" ] } {
            incr i
            incr count
            set t [ lindex $V $i ]
            lappend A($c) $t
        } else {
            lappend A($c) {}
        }
        incr i
        incr count
    }
}

proc _getopts_parse_opt_string { C_name opts } {
    upvar $C_name C
    set i 0
    set N [ string length $opts ]
    while { $i < $N } {
        set c     [ string range $opts $i $i ]
        incr i
        set colon [ string range $opts $i $i ]
        if { [ string equal $colon ":" ] } {
            set C($c) ":"
            incr i
        } else {
            set C($c) "_"
        }
    }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 2
# End:
