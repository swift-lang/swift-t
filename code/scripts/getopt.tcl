
# From: http://www2.tcl.tk/22050

package require Tcl 8.6

proc getopt {optvar argvar list body} {
    upvar 1 $optvar option $argvar value
    set arg(missing) [dict create pattern missing argument 0]
    set arg(unknown) [dict create pattern unknown argument 0]
    foreach {pat code} $body {
       switch -glob -- $pat {
           -- {# end-of-options option}
           --?*: {# long option requiring an argument
               set arg([string range $pat 0 end-1]) \
                 [dict create pattern $pat argument 1]
           }
           --?* {# long option without an argument
               set arg($pat) [dict create pattern $pat argument 0]
           }
           -?* {# short options
               set last ""; foreach c [split [string range $pat 1 end] ""] {
                   if {$c eq ":" && $last ne ""} {
                       dict set arg($last) argument 1
                       set last ""
                   } else {
                       set arg(-$c) [dict create pattern $pat argument 0]
                       set last -$c
                   }
               }
           }
       }
    }
    while {[llength $list]} {
       set rest [lassign $list opt]
       # Does it look like an option?
       if {$opt eq "-" || [string index $opt 0] ne "-"} break
       # Is it the end-of-options option?
       if {$opt eq "--"} {set list $rest; break}
       set option [string range $opt 0 1]
       set value 1
       if {$option eq "--"} {
           # Long format option
           if {[info exists arg($opt)]} {
               set option $opt
           } elseif {[llength [set match [array names arg $opt*]]] == 1} {
               set option [lindex $match 0]
           } else {
               # Unknown or ambiguous option
               set value $opt
               set option unknown
           }
           if {[dict get $arg($option) argument]} {
               if {[llength $rest]} {
                   set rest [lassign $rest value]
               } else {
                   set value $option
                   set option missing
               }
           }
       } elseif {![info exists arg($option)]} {
           set value $option
           set option unknown
           if {[string length $opt] > 2} {
               set rest [lreplace $list 0 0 [string replace $opt 1 1]]
           }
       } elseif {[dict get $arg($option) argument]} {
           if {[string length $opt] > 2} {
               set value [string range $opt 2 end]
           } elseif {[llength $rest]} {
               set rest [lassign $rest value]
           } else {
               set value $option
               set option missing
           }
       } elseif {[string length $opt] > 2} {
           set rest [lreplace $list 0 0 [string replace $opt 1 1]]
       }
       uplevel 1 [list switch -- [dict get $arg($option) pattern] $body]
       set list $rest
    }
    set option arglist
    set value $list
    uplevel 1 [list switch -- arglist $body]
}
