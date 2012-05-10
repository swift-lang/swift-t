# Turbine builtin arithmetic functions

# For two types: integer and float
# All have the same signature
#   f <STACK> <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs



namespace eval turbine {
    proc plus_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "plus-$a-$b" "$a $b" $turbine::LOCAL \
            "plus_integer_body $parent $c $a $b"
    }

    proc plus_integer_body { parent c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value + $b_value ]
        log "plus: $a_value + $b_value => $c_value"
        store_integer $c $c_value
    }

    proc plus_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "plus-$a-$b" "$a $b" $turbine::LOCAL \
            "plus_float_body $parent $c $a $b"
    }

    proc plus_float_body { parent c a b } {
        set a_value [ retrieve_float $a ]
        set b_value [ retrieve_float $b ]
        set c_value [ expr $a_value + $b_value ]
        log "plus: $a_value + $b_value => $c_value"
        store_float $c $c_value
    }

    # This is a Swift-2 function
    # c = a-b;
    # and sleeps for c seconds
    proc minus_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "minus-$a-$b" "$a $b" $turbine::LOCAL \
            "minus_integer_body $c $a $b"
    }
    proc minus_integer_body {c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value - $b_value ]
        log "minus: $a_value - $b_value => $c_value"
        store_integer $c $c_value
    }

    # This is a Swift-5 function
    # c = a-b;
    # and sleeps for c seconds
    proc minus_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "minus-$a-$b" "$a $b" $turbine::LOCAL \
            "minus_float_body $c $a $b"
    }
    proc minus_float_body {c a b } {
        set a_value [ retrieve_float $a ]
        set b_value [ retrieve_float $b ]
        set c_value [ expr $a_value - $b_value ]
        log "minus: $a_value - $b_value => $c_value"
        store_float $c $c_value
    }

    # c = a*b;
    # and sleeps for c seconds
    proc multiply_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "mult-$a-$b" "$a $b" $turbine::LOCAL \
            "multiply_integer_body $c $a $b"
    }
    proc multiply_integer_body {c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value * $b_value ]
        log "multiply: $a_value * $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        store_integer $c $c_value
    }

    # c = a*b;
    # and sleeps for c seconds
    proc multiply_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "mult-$a-$b" "$a $b" $turbine::LOCAL \
            "multiply_float_body $c $a $b"
    }
    proc multiply_float_body {c a b } {
        set a_value [ retrieve_float $a ]
        set b_value [ retrieve_float $b ]
        set c_value [ expr $a_value * $b_value ]
        log "multiply: $a_value * $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        store_float $c $c_value
    }

    # c = a/b; with integer division
    # and sleeps for c seconds
    proc divide_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "div-$a-$b" "$a $b" $turbine::LOCAL \
            "divide_integer_body $c $a $b"
    }
    proc divide_integer_body {c a b } {
        set a_value [ retrieve_integer $a ]
        set b_value [ retrieve_integer $b ]

        set c_value [ divide_integer_impl $a_value $b_value ]
        log "divide: $a_value / $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        store_integer $c $c_value
    }

    # c = a/b; with float division
    # and sleeps for c seconds
    proc divide_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "div-$a-$b" "$a $b" $turbine::LOCAL \
            "divide_float_body $c $a $b"
    }
    proc divide_float_body {c a b } {
        set a_value [ retrieve_float $a ]
        set b_value [ retrieve_float $b ]
        set c_value [ expr $a_value / $b_value ]
        log "divide: $a_value / $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        store_float $c $c_value
    }

    # This is a Swift-2 function
    # c = -b;
    proc negate_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]

        rule "negate-$a" "$a" $turbine::LOCAL \
            "negate_integer_body $c $a"
    }

    proc negate_integer_body { c a } {
        set a_value [ get $a ]
        set c_value [ expr 0 - $a_value ]
        log "negate: -1 * $a_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        store_integer $c $c_value
    }

    # This is a Swift-5 function
    # c = -b;
    proc negate_float { parent c inputs } {
        set a [ lindex $inputs 0 ]

        rule "negate-$a" "$a" $turbine::LOCAL \
            "negate_float_body $c $a"
    }

    proc negate_float_body { c a } {
        set a_value [ retrieve_float $a ]
        set c_value [ expr 0 - $a_value ]
        log "negate: -1 * $a_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        store_float $c $c_value
    }


    proc mod_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "mod-$a-$b" "$a $b" $turbine::LOCAL \
            "mod_integer_body $parent $c $a $b"
    }

    proc mod_integer_body { parent c a b } {
        set a_value [ retrieve_integer $a ]
        set b_value [ retrieve_integer $b ]
        set c_value [ mod_integer_impl $a_value $b_value ]
        log "mod: $a_value % $b_value => $c_value"
        store_integer $c $c_value
    }

    # emulate java's mod and div operator behaviour for negative numbers
    # tcl uses floored integer division, while java used truncated integer
    # division.  Both tcl and java implementations are the same for
    # positive numbers
    proc divide_integer_impl { a b } {
        # emulate java's truncated integer division
        # e.g so -5/3 = -1 and 4/-3 = -1
        # java's integer division obeys the rule:
        # a / b == sign(a) * sign(b) * ( abs(a) / abs(b) )
        set sign 1
        if { [ expr $a < 0 ] } {
            set sign [ expr $sign * -1 ]
        }
        if { [ expr $b < 0 ] } {
            set sign [ expr $sign * -1 ]
        }
        return [ expr $sign * ( abs($a) / abs($b) ) ]
    }

    proc mod_integer_impl { a b } {
        # in Java's truncated integer division,
        # a % b == sign(a) * ( abs(a) % abs(b) )
        if { [ expr $a >= 0 ] } {
            set sign 1
        } else {
            set sign -1
        }
        return [ expr $sign * ( abs($a) % abs($b) ) ]
    }

    # This is a Swift-2 function, thus it only applies to integers
    # o = i;
    proc copy_integer { parent o i } {

        rule "copy-$o-$i" $i $turbine::LOCAL \
            "copy_integer_body $o $i"
    }
    proc copy_integer_body { o i } {
        set i_value [ get $i ]
        set o_value $i_value
        log "copy $i_value => $o_value"
        store_integer $o $o_value
    }

    # o = i;
    proc copy_float { parent o i } {
        rule "copy_float-$o-$i" $i $turbine::LOCAL \
            "copy_float_body $o $i"
    }
    proc copy_float_body { o i } {
        set i_value [ retrieve_float $i ]
        log "copy $i_value => $i_value"
        store_float $o $i_value
    }

    proc max_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "max-$a-$b" "$a $b" $turbine::LOCAL \
            "max_integer_body $parent $c $a $b"
    }

    proc max_integer_body { parent c a b } {
        set a_value [ retrieve_integer $a ]
        set b_value [ retrieve_integer $b ]
        set c_value [ expr max ($a_value, $b_value) ]
        log "max: $a_value $b_value => $c_value"
        store_integer $c $c_value
    }

    proc min_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "min-$a-$b" "$a $b" $turbine::LOCAL \
            "min_integer_body $parent $c $a $b"
    }

    proc min_integer_body { parent c a b } {
        set a_value [ retrieve_integer $a ]
        set b_value [ retrieve_integer $b ]
        set c_value [ expr min ($a_value, $b_value) ]
        log "min: $a_value $b_value => $c_value"
        store_integer $c $c_value
    }

    proc max_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "max-$a-$b" "$a $b" $turbine::LOCAL \
            "max_float_body $parent $c $a $b"
    }

    proc max_float_body { parent c a b } {
        set a_value [ retrieve_float $a ]
        set b_value [ retrieve_float $b ]
        set c_value [ expr max ($a_value, $b_value) ]
        log "max: $a_value $b_value => $c_value"
        store_float $c $c_value
    }

    proc min_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "min-$a-$b" "$a $b" $turbine::LOCAL \
            "min_float_body $parent $c $a $b"
    }

    proc min_float_body { parent c a b } {
        set a_value [ retrieve_float $a ]
        set b_value [ retrieve_float $b ]
        set c_value [ expr min ($a_value, $b_value) ]
        log "min: $a_value $b_value => $c_value"
        store_float $c $c_value
    }

    proc floor { parent c a } {

        rule "floor-$a" "$a" $turbine::LOCAL \
            "floor_body $parent $c $a"
    }

    proc floor_body { parent c a } {
        set a_value [ retrieve_float $a ]
        set c_value [ expr floor($a_value) ]
        log "floor: $a_value => $c_value"
        store_integer $c $c_value
    }

    proc ceil { parent c a } {

        rule "ceil-$a" "$a" $turbine::LOCAL \
            "ceil_body $parent $c $a"
    }

    proc ceil_body { parent c a } {
        set a_value [ retrieve_float $a ]
        set c_value [ expr ceil($a_value) ]
        log "ceil: $a_value => $c_value"
        store_integer $c $c_value
    }

    proc round { parent c a } {
        rule "round-$a" "$a" $turbine::LOCAL \
            "round_body $parent $c $a"
    }

    proc round_body { parent c a } {
        set a_value [ retrieve_float $a ]
        set c_value [ expr round($a_value) ]
        log "round: $a_value => $c_value"
        store_integer $c $c_value
    }

    proc itof { parent c a } {
        rule "itf-$a" "$a" $turbine::LOCAL \
            "itof_body $parent $c $a"
    }

    proc itof_body { parent c a } {
        set a_value [ retrieve_integer $a ]
        store_float $c $a_value
    }

    proc log_e { parent c a } {

        rule "log_e-$a" "$a" $turbine::LOCAL \
            "log_e_body $parent $c $a"
    }

    proc log_e_body { parent c a } {
        set a_value [ retrieve_float $a ]
        set c_value [ expr log($a_value) ]
        log "log_e: $a_value => $c_value"
        store_float $c $c_value
    }

    proc exp { parent c a } {

        rule "exp-$a" "$a" $turbine::LOCAL \
            "exp_body $parent $c $a"
    }

    proc exp_body { parent c a } {
        set a_value [ retrieve_float $a ]
        set c_value [ expr exp($a_value) ]
        log "exp: $a_value => $c_value"
        store_float $c $c_value
    }

    proc sqrt { parent c a } {

        rule "sqrt-$a" "$a" $turbine::LOCAL \
            "sqrt_body $parent $c $a"
    }

    proc sqrt_body { parent c a } {
        set a_value [ retrieve_float $a ]
        set c_value [ expr sqrt($a_value) ]
        log "sqrt: $a_value => $c_value"
        store_float $c $c_value
    }

    proc abs_float { parent c a } {

        rule "abs_float-$a" "$a" $turbine::LOCAL \
            "abs_float_body $parent $c $a"
    }

    proc abs_float_body { parent c a } {
        set a_value [ retrieve_float $a ]
        set c_value [ expr abs($a_value) ]
        log "abs_float: $a_value => $c_value"
        store_float $c $c_value
    }

    proc abs_integer { parent c a } {

        rule "abs_integer-$a" "$a" $turbine::LOCAL \
            "abs_integer_body $parent $c $a"
    }

    proc abs_integer_body { parent c a } {
        set a_value [ retrieve_integer $a ]
        set c_value [ expr abs($a_value) ]
        log "abs_integer: $a_value => $c_value"
        store_integer $c $c_value
    }

    proc pow_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "pow-$a-$b" "$a $b" $turbine::LOCAL \
            "pow_integer_body $c $a $b"
    }
    proc pow_integer_body { c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        # convert to float otherwise doesn't handle negative
        # exponents right
        set c_value [ pow_integer_impl $a_value $b_value ]
        log "pow_integer: $a_value ** $b_value => $c_value"
        store_float $c $c_value
    }

    proc pow_integer_impl { a b } {
        if { $b < 0 } {
            set a [ expr double($a) ]
            set b [ expr double($b) ]
        }
        return [ expr $a ** $b ]
    }

    proc pow_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "pow-$a-$b" "$a $b" $turbine::LOCAL \
            "pow_float_body $c $a $b"
    }
    proc pow_float_body {c a b } {
        set a_value [ retrieve_float $a ]
        set b_value [ retrieve_float $b ]
        set c_value [ expr $a_value ** $b_value ]
        log "pow_float: $a_value ** $b_value => $c_value"
        store_float $c $c_value
    }

    # checks to see if float i is NaN, sets o to true or false accordingly
    proc is_nan { stack o i } {
        rule "is_nan-$o-$i" "$i" $turbine::LOCAL \
            "is_nan_body $o $i"
    }
    proc is_nan_body { o i } {
      set i_value [ retrieve_float $i ]
      # NaN is the only floating point value not equal to itself
      store_integer $o [ expr $i_value != $i_value ]
    }
}
