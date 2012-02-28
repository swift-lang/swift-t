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

        set rule_id [ rule_new ]
        rule $rule_id "strcat-$rule_id" $inputs $result \
            "tp: strcat2_body $inputs $result"
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
        set rule_id [ rule_new ]
        set str [ lindex $inputs 0 ]
        set first [ lindex $inputs 1 ]
        set len [ lindex $inputs 2 ]
        rule $rule_id "substring-$rule_id-$str-$first-$len" $inputs $result \
            "tp: substring_body $result $str $first $len"
    }

    proc substring_body { result str first len } {
        set str_val   [ get $str ]
        set first_val [ get $first ]
        set len_val   [ get $len ]

        set last [ expr $first_val + $len_val - 1 ]
        set result_val [ string range $str_val $first_val $last ]
        set_string $result $result_val
    }

    proc strcat { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "strcat-$a-$b" "$a $b" $c \
            "tl: strcat_body $parent $c $a $b"
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
        set rule_id [ rule_new ]
        rule $rule_id "copystring-$o-$i" $i $o \
            "tl: copy_string_body $o $i"
    }
    proc copy_string_body { o i } {
        set i_value [ get_string $i ]
        log "copy $i_value => $i_value"
        set_string $o $i_value
    }
}
