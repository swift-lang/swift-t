
# Turbine builtin string functions

# All have the same signature
#   f <STACK> <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs

namespace eval turbine {

    # User function
    # usage: strcat <result> <args>*
    proc strcat { stack result inputs } {
        rule "strcat" $inputs $turbine::LOCAL \
            "strcat_body $result $inputs"
    }

    # usage: strcat_body <result> <args>*
    proc strcat_body { result args } {
        set output [ list ]
        foreach input $args {
            set t [ retrieve_string $input ]
            lappend output $t
        }
        set total [ join $output "" ]
        store_string $result $total
    }

    # Substring of s starting at i of length n
    proc substring { stack result inputs  } {

        set s [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set n [ lindex $inputs 2 ]
        rule "substring-$s-$i-$n" $inputs $turbine::LOCAL \
            "substring_body $result $s $i $n"
    }

    proc substring_body { result s i n } {
        set s_value [ retrieve_string  $s ]
        set i_value [ retrieve_integer $i ]
        set n_value [ retrieve_integer $n ]

        set result_value [ substring_impl $s_value $i_value $n_value ]
        store_string $result $result_value
    }

    proc substring_impl { s i n } {
        set last [ expr $i + $n - 1 ]
        return [ string range $s $i $n ]
    }

    # This accepts an optional delimiter
    # (STC does not yet support optional arguments)
    proc split { args } {
        set result [ lindex $args 1 ]
        set inputs [ lreplace $args 0 1 ]

        # Unpack inputs
        set inputs [ lindex $inputs 0 ]

        set s [ lindex $inputs 0 ]
        if { [ llength $inputs ] == 2 } {
            set delimiter [ lindex $inputs 1 ]
            rule "split-$result" [ list $s $delimiter ] \
                $turbine::LOCAL \
                "split_body $result $s $delimiter"
        } elseif { [ llength $inputs ] == 1 } {
            # Use default delimiter: " "
            set delimiter 0
            rule "split-$result" $s $turbine::LOCAL \
                "split_body $result $s 0"
        } else {
            error "split requires 1 or 2 arguments"
        }
    }

    # Split string s with delimiter d into result container r
    # Tcl split should handle spaces correctly:
    # http://tmml.sourceforge.net/doc/tcl/split.html
    proc split_body { result s delimiter } {
        set s_value [ retrieve_string $s ]
        if { $delimiter == 0 } {
            set d_value " "
        } else {
            set d_value [ retrieve_string $delimiter ]
        }
        set r_value [ ::split $s_value $d_value ]
        set n [ llength $r_value ]
        log "split: $s_value on: $d_value tokens: $n"
        for { set i 0 } { $i < $n } { incr i } {
            set v [ lindex $r_value $i ]
            literal split_token string $v
            container_insert $result $i $split_token
        }
        close_datum $result
    }
    
    proc sprintf { stack result inputs } {
        rule sprintf $inputs $turbine::LOCAL \
            "sprintf_body $result $inputs"
    }
    proc sprintf_body { result args } {
        set L [ list ]
        foreach a $args {
            lappend L [ retrieve $a ]
        }
        set s [ eval format $L ]
        store_string $result $s
    }
}
