# Turbine builtin string functions

# All have the same signature
#   f <STACK> <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs

namespace eval turbine {

    # User function
    # usage: strcat <result> <args>*
    proc strcat2 { args } {

        set result [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]

        rule "strcat" $inputs $result \
            "strcat2_body $inputs $result"
    }

    # usage: strcat_body <args>* <result>
    proc strcat2_body { args } {

        set result [ lindex $args end ]
        set inputs [ lreplace $args end end ]

        set output [ list ]
        foreach input $inputs {
            set t [ get $input ]
            lappend output $t
        }
        set total [ join $output "" ]
        set_string $result $total
    }

    proc substring { stack result inputs  } {

        set str [ lindex $inputs 0 ]
        set first [ lindex $inputs 1 ]
        set len [ lindex $inputs 2 ]
        rule "substring-$str-$first-$len" $inputs \
            $turbine::LOCAL \
            "substring_body $result $str $first $len"
    }

    proc substring_body { result str first len } {
        set str_val   [ get $str ]
        set first_val [ get $first ]
        set len_val   [ get $len ]

        set result_val [ substring_impl $str_val $first_val $len_val ]
        set_string $result $result_val
    }

    proc substring_impl { str first len } {
        set last [ expr $first + $len - 1 ]
        return [ string range $str $first $last ]
    }

    proc strcat { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "strcat-$a-$b" "$a $b" $turbine::LOCAL \
            "strcat_body $parent $c $a $b"
    }

    proc strcat_body { parent c a b } {
        set a_value [ get_string $a ]
        set b_value [ get_string $b ]
        set c_value "${a_value}${b_value}"
        log "strcat: strcat($a_value, $b_value) => $c_value"
        set_string $c $c_value
    }

    # o = i;
    proc copy_string { parent o i } {

        rule "copystring-$o-$i" $i $turbine::LOCAL \
            "copy_string_body $o $i"
    }
    proc copy_string_body { o i } {
        set i_value [ get_string $i ]
        log "copy $i_value => $i_value"
        set_string $o $i_value
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
        set s_value [ get_string $s ]
        if { $delimiter == 0 } {
            set d_value " "
        } else {
            set d_value [ get_string $delimiter ]
        }
        set r_value [ ::split $s_value $d_value ]
        set n [ llength $r_value ]
        log "split: $s_value on: $d_value tokens: $n"
        for { set i 0 } { $i < $n } { incr i } {
            set v [ lindex $r_value $i ]
            literal split_token string $v
            container_insert $result $i $split_token
        }
        close_container $result
    }
}
