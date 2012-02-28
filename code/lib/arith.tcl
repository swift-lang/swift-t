# Turbine builtin arithmetic functions

# For two types: integer and float
# All have the same signature
#   f <STACK> <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs



namespace eval turbine {
    proc plus_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "plus-$a-$b" "$a $b" $c \
            "tl: plus_integer_body $parent $c $a $b"
    }

    proc plus_integer_body { parent c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value + $b_value ]
        log "plus: $a_value + $b_value => $c_value"
        set_integer $c $c_value
    }

    proc plus_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "plus-$a-$b" "$a $b" $c \
            "tl: plus_float_body $parent $c $a $b"
    }

    proc plus_float_body { parent c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        set c_value [ expr $a_value + $b_value ]
        log "plus: $a_value + $b_value => $c_value"
        set_float $c $c_value
    }

    # This is a Swift-2 function
    # c = a-b;
    # and sleeps for c seconds
    proc minus_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "minus-$a-$b" "$a $b" $c \
            "tl: minus_integer_body $c $a $b"
    }
    proc minus_integer_body {c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value - $b_value ]
        log "minus: $a_value - $b_value => $c_value"
        set_integer $c $c_value
    }

    # This is a Swift-5 function
    # c = a-b;
    # and sleeps for c seconds
    proc minus_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "minus-$a-$b" "$a $b" $c \
            "tl: minus_float_body $c $a $b"
    }
    proc minus_float_body {c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        set c_value [ expr $a_value - $b_value ]
        log "minus: $a_value - $b_value => $c_value"
        set_float $c $c_value
    }

    # c = a*b;
    # and sleeps for c seconds
    proc multiply_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "mult-$a-$b" "$a $b" $c \
            "tf: multiply_integer_body $c $a $b"
    }
    proc multiply_integer_body {c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value * $b_value ]
        log "multiply: $a_value * $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        set_integer $c $c_value
    }

    # c = a*b;
    # and sleeps for c seconds
    proc multiply_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "mult-$a-$b" "$a $b" $c \
            "tf: multiply_float_body $c $a $b"
    }
    proc multiply_float_body {c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        set c_value [ expr $a_value * $b_value ]
        log "multiply: $a_value * $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        set_float $c $c_value
    }

    # c = a/b; with integer division
    # and sleeps for c seconds
    proc divide_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "div-$a-$b" "$a $b" $c \
            "tf: divide_integer_body $c $a $b"
    }
    proc divide_integer_body {c a b } {
        set a_value [ get_integer $a ]
        set b_value [ get_integer $b ]
        set c_value [ expr $a_value / $b_value ]
        log "divide: $a_value / $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        set_integer $c $c_value
    }

    # c = a/b; with float division
    # and sleeps for c seconds
    proc divide_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "div-$a-$b" "$a $b" $c \
            "tf: divide_float_body $c $a $b"
    }
    proc divide_float_body {c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        set c_value [ expr $a_value / $b_value ]
        log "divide: $a_value / $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        set_float $c $c_value
    }
    
    # This is a Swift-2 function
    # c = -b;
    proc negate_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set rule_id [ rule_new ]
        rule $rule_id "negate-$a" "$a" $c \
            "tf: negate_integer_body $c $a"
    }

    proc negate_integer_body { c a } {
        set a_value [ get $a ]
        set c_value [ expr 0 - $a_value ]
        log "negate: -1 * $a_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        set_integer $c $c_value
    }

    # This is a Swift-5 function
    # c = -b;
    proc negate_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set rule_id [ rule_new ]
        rule $rule_id "negate-$a" "$a" $c \
            "tf: negate_float_body $c $a"
    }

    proc negate_float_body { c a } {
        set a_value [ get_float $a ]
        set c_value [ expr 0 - $a_value ]
        log "negate: -1 * $a_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        set_float $c $c_value
    }

    # This is a Swift-2 function, thus it only applies to integers
    # o = i;
    proc copy_integer { parent o i } {
        set rule_id [ rule_new ]
        rule $rule_id "copy-$o-$i" $i $o \
            "tl: copy_integer_body $o $i"
    }
    proc copy_integer_body { o i } {
        set i_value [ get $i ]
        set o_value $i_value
        log "copy $i_value => $o_value"
        set_integer $o $o_value
    }

    # o = i;
    proc copy_float { parent o i } {
        set rule_id [ rule_new ]
        rule $rule_id "copyfloat-$o-$i" $i $o \
            "tl: copy_float_body $o $i"
    }
    proc copy_float_body { o i } {
        set i_value [ get_float $i ]
        log "copy $i_value => $i_value"
        set_float $o $i_value
    }
}
