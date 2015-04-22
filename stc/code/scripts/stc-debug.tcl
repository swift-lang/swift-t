
set STC $env(STC)

source $STC/scripts/getopt.tcl

proc cat { args } {
    return [ join $args " " ]
}

proc help { } {
    puts [ cat "usage: stc-debug [-l <LOG>] [-t <TCL>] [-s <SWIFT>]"
               "                 [-d <DATA>] [-y <SYMBOL>]" ]
}

proc show { v } {
    upvar $v t
    puts "$v: $t"
}

proc crash { msg } {
    puts $msg
    exit 1
}

proc assert { condition msg } {
    if { ! [ uplevel 1 "expr $condition" ] } {
        crash $msg
    }
}

proc verbose { level msg } {
    global verbosity
    if { $verbosity >= level } {
        puts $msg
    }
}

proc spaces { count } {
    for { set i 0 } { $i < $count } { incr i } {
        puts -nonewline " "
    }
}

set verbosity 0
set logfile ""
set tclfile ""
set swiftfile ""
set td ""
set symbol ""

getopt flag arg $argv {
    -h? - --help {
        help
    }
    -v {
        # Increase the verbosity level
        incr verbosity
    }
    --verbose: {
        # Set the verbosity level
        set verbosity $arg
    }
    -d: {
        set td $arg
    }
    -l: {
        set logfile $arg
    }
    -s: {
        set swiftfile $arg
    }
    -t: {
        set tclfile $arg
    }
    -y: {
        set symbol $arg
    }
    missing {
        puts stderr "option requires argument: $arg"
        exit 2
    }
    unknown {
        puts stderr "unknown or ambiguous option: $arg"
        exit 2
    }
    arglist {
        set files $arg
    }
}

if { $verbosity > 0 } {
    show logfile
    show tclfile
    show swiftfile
}

set has_logfile   [ expr [ string length $logfile   ] > 0 ]
set has_tclfile   [ expr [ string length $tclfile   ] > 0 ]
set has_swiftfile [ expr [ string length $swiftfile ] > 0 ]

set has_td     [ expr [ string length $td     ] > 0 ]
set has_symbol [ expr [ string length $symbol ] > 0 ]

assert { $has_logfile || $has_tclfile || $has_swiftfile } \
    "Provide a Turbine log and/or Tcl file and/or Swift file"

assert { ! ( $has_td && $has_symbol ) } \
    "Provide only a datum or symbol"

assert { $has_td || $has_symbol } "Provide a datum or symbol"

# regexp pattern for typical token
set token "(\[\[:graph:\]\]*)"

if { $has_td } {
    assert { $has_logfile } "logfile required for datum lookup!"
    try {
        set results [ exec grep "\[<\]$td" $logfile 2>@1 ]
        set status 0
    } trap CHILDSTATUS {} {
        puts "datum not found in log: <$td>"
        exit 1
    }
    if [ regexp " $token=<$td>" $results x symbol ] {
        puts "<$td> is an instance of $symbol"
        set has_symbol 1
    } else {
        crash "could not find information for <$td>"
    }
}

# At this point, we must have the symbol

# If we do not have the Tcl file, we cannot report anything else
if { ! $has_tclfile } { exit 0 }

try {
    set results [ exec grep "Var: .* $symbol" $tclfile 2>@1 ]
    set status 0
} trap CHILDSTATUS {} {
    crash "datum not found in log: <$td>"
}

set pattern " Var: $token $symbol $token $token"
if [ regexp $pattern $results x type usage location ] {
    puts "$symbol is of type $type used as $usage at $location"
} else {
    crash "symbol not found in Tcl source: $symbol"
}

# If we do not have the Swift file, we cannot report anything else
if { ! $has_swiftfile } { exit 0 }

set digit "(\[\[:digit:\]\]*)"

set pattern "${token}:${token}:${digit}:${digit}"
if { ! [ regexp $pattern $location x filename function line column ] } {
    crash "could not parse location: $location"
}

puts ""
exec sed -n ${line}p $swiftfile > /dev/stdout
spaces $column
puts "^"
