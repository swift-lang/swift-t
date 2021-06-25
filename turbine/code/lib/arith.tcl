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

# Turbine builtin arithmetic functions

# For two types: integer and float
# All have the same signature
#   f <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs

namespace eval turbine {

    proc plus_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "plus_integer_body $c $a $b" \
            name "plus-$a-$b"
    }

    proc plus_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        set c_value [ expr {$a_value + $b_value} ]
        log "plus: $a_value + $b_value => $c_value"
        store_integer $c $c_value
    }

    proc plus_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "plus_float_body $c $a $b" \
            name "plus-$a-$b"
    }

    proc plus_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        set c_value [ expr {$a_value + $b_value} ]
        log "plus: $a_value + $b_value => $c_value"
        store_float $c $c_value
    }

    # This is a Swift-2 function
    # c = a-b;
    # and sleeps for c seconds
    proc minus_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule  "$a $b" "minus_integer_body $c $a $b" \
            name "minus-$a-$b"
    }
    proc minus_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        set c_value [ expr {$a_value - $b_value} ]
        log "minus: $a_value - $b_value => $c_value"
        store_integer $c $c_value
    }

    # c = a-b;
    proc minus_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "minus_float_body $c $a $b" \
            name "minus-$a-$b"
    }
    proc minus_float_body {c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        set c_value [ expr {$a_value - $b_value} ]
        log "minus: $a_value - $b_value => $c_value"
        store_float $c $c_value
    }

    # c = a*b;
    proc multiply_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule  "$a $b" "multiply_integer_body $c $a $b" \
            name "mult-$a-$b"
    }
    proc multiply_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        set c_value [ expr {$a_value * $b_value} ]
        log "multiply: $a_value * $b_value => $c_value"
        store_integer $c $c_value
    }

    # c = a*b;
    proc multiply_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "multiply_float_body $c $a $b" \
            name "mult-$a-$b"
    }
    proc multiply_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        set c_value [ expr {$a_value * $b_value} ]
        log "multiply: $a_value * $b_value => $c_value"
        store_float $c $c_value
    }

    # c = a/b; with integer division
    proc divide_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "divide_integer_body $c $a $b" \
            name "div-$a-$b"
    }
    proc divide_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]

        set c_value [ divide_integer_impl $a_value $b_value ]
        log "divide: $a_value / $b_value => $c_value"
        store_integer $c $c_value
    }

    # c = a/b; with float division
    # and sleeps for c seconds
    proc divide_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b"  "divide_float_body $c $a $b" \
            name "div-$a-$b"
    }
    proc divide_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        set c_value [ expr {$a_value / $b_value} ]
        log "divide: $a_value / $b_value => $c_value"
        store_float $c $c_value
    }

    # c = -a;
    proc negate_integer { c a } {
        rule $a "negate_integer_body $c $a" name "negate-$a"
    }

    proc negate_integer_body { c a } {
        set a_value [ retrieve_decr_integer $a ]
        set c_value [ expr {0 - $a_value} ]
        log "negate: -1 * $a_value => $c_value"
        store_integer $c $c_value
    }

    # c = -a;
    proc negate_float { c a } {
        rule $a "negate_float_body $c $a" name "negate-$a"
    }

    proc negate_float_body { c a } {
        set a_value [ retrieve_decr_float $a ]
        set c_value [ expr {0 - $a_value} ]
        log "negate: -1 * $a_value => $c_value"
        store_float $c $c_value
    }


    proc mod_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "mod_integer_body $c $a $b" name "mod-$a-$b"
    }

    proc mod_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
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
        if { [ expr {$a < 0} ] } {
            set sign [ expr {$sign * -1} ]
        }
        if { [ expr {$b < 0} ] } {
            set sign [ expr {$sign * -1} ]
        }
        return [ expr {$sign * ( abs($a) / abs($b) )} ]
    }

    proc mod_integer_impl { a b } {
        # in Java's truncated integer division,
        # a % b == sign(a) * ( abs(a) % abs(b) )
        if { [ expr {$a >= 0} ] } {
            set sign 1
        } else {
            set sign -1
        }
        return [ expr {$sign * ( abs($a) % abs($b) )} ]
    }

    proc max_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "max_integer_body $c $a $b" name "max-$a-$b"
    }

    proc max_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        set c_value [ expr {max ($a_value, $b_value)} ]
        log "max: $a_value $b_value => $c_value"
        store_integer $c $c_value
    }

    proc min_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "min_integer_body $c $a $b" name "min-$a-$b"
    }

    proc min_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        set c_value [ expr {min ($a_value, $b_value)} ]
        log "min: $a_value $b_value => $c_value"
        store_integer $c $c_value
    }

    proc max_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "max_float_body $c $a $b" name "max-$a-$b"
    }

    proc max_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        set c_value [ expr {max ($a_value, $b_value)} ]
        log "max: $a_value $b_value => $c_value"
        store_float $c $c_value
    }

    proc min_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "min_float_body $c $a $b" name "min-$a-$b"
    }

    proc min_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        set c_value [ expr {min ($a_value, $b_value)} ]
        log "min: $a_value $b_value => $c_value"
        store_float $c $c_value
    }

    proc floor { c a } {
        rule "$a" "floor_body $c $a"
    }

    proc floor_body { c a } {
        set a_value [ retrieve_decr_float $a ]
        set c_value [ expr {floor($a_value)} ]
        log "floor: $a_value => $c_value"
        store_float $c $c_value
    }

    proc int2float { c a } {
        rule "$a" "int2float_body $c $a"
    }
    proc int2float_body { c a } {
        puts "int2float retreive a: $a"
        set a_value [ retrieve_decr_integer $a ]
        log "int2float: $a_value"
        store_float $c $a_value
    }

    proc float2int { result input } {
        rule $input "float2int_body $result $input " \
            name "float2int-$input"
    }
    proc float2int_body { result input } {
        set t [ retrieve_decr_float $input ]
        set i [ float2int_impl $t ]
        store_integer $result $i
    }
    proc float2int_impl { f } {
        return [ expr int($f) ]
    }

    proc ceil { c a } {
        rule $a "ceil_body $c $a" name "round-$a"
    }

    proc ceil_body { c a } {
        set a_value [ retrieve_decr_float $a ]
        set c_value [ expr {ceil($a_value)} ]
        log "ceil: $a_value => $c_value"
        store_float $c $c_value
    }

    proc round { c a } {
        rule $a "round_body $c $a" name "round-$a"
    }

    proc round_body { c a } {
        set a_value [ retrieve_decr_float $a ]
        set c_value [ expr {round($a_value)} ]
        log "round: $a_value => $c_value"
        store_float $c $c_value
    }

    proc itof { c a } {
        rule $a "itof_body $c $a" name "itf-$a"
    }

    proc itof_body { c a } {
        set a_value [ retrieve_decr_integer $a ]
        # Convert to TCL float type
        store_float $c [ expr {double($a_value)} ]
    }

    proc log_e { c a } {
        rule $a "log_e_body $c $a"
    }

    proc log_e_body { c a } {
        set a_value [ retrieve_decr_float $a ]
        if [ catch { set c_value [ expr {log($a_value)} ] } e ] {
            turbine_error "log_e($c_value): $e"
        }
        log "log_e: $a_value => $c_value"
        store_float $c $c_value
    }

    proc log10_impl { v } {
        if [ catch { set result [ ::tcl::mathfunc::log10 $v ] } e ] {
            turbine_error "log10($v): $e"
        }
        return $result
    }

    proc log_base_impl { x base } {
        if [ catch { set result [ expr {log($x)/log($base)} ] } e ] {
            turbine_error "log($x,$base): $e"
        }
        return $result
    }

    proc exp { c a } {
        rule $a "exp_body $c $a" name "exp-$a"
    }

    proc exp_body { c a } {
        set a_value [ retrieve_decr_float $a ]
        set c_value [ expr {exp($a_value)} ]
        log "exp: $a_value => $c_value"
        store_float $c $c_value
    }

    proc sqrt { c a } {
        rule $a "sqrt_body $c $a" name "sqrt-$a"
    }

    proc sqrt_body { c a } {
        set a_value [ retrieve_decr_float $a ]
        if [ catch { set c_value [ expr {sqrt($a_value)} ] } e ] {
            turbine_error "sqrt($a_value): $e"
        }
        log "sqrt: $a_value => $c_value"
        store_float $c $c_value
    }

    proc abs_float { c a } {
        rule $a "abs_float_body $c $a" name "abs_float-$a"
    }

    proc abs_float_body { c a } {
        set a_value [ retrieve_decr_float $a ]
        set c_value [ expr {abs($a_value)} ]
        log "abs_float: $a_value => $c_value"
        store_float $c $c_value
    }

    proc abs_integer { c a } {
        rule $a "abs_integer_body $c $a" name "abs_integer-$a"
    }

    proc abs_integer_body { c a } {
        set a_value [ retrieve_decr_integer $a ]
        set c_value [ expr {abs($a_value)} ]
        log "abs_integer: $a_value => $c_value"
        store_integer $c $c_value
    }

    proc pow_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "pow_integer_body $c $a $b" \
            name "pow-$a-$b"
    }
    proc pow_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        # convert to float otherwise doesn't handle negative
        # exponents right
        set c_value [ pow_integer_impl $a_value $b_value ]
        log "pow_integer: $a_value ** $b_value => $c_value"
        store_float $c $c_value
    }

    proc pow_integer_impl { a b } {
        if { $b < 0 } {
            set a [ expr {double($a)} ]
            set b [ expr {double($b)} ]
        }
        return [ expr {$a ** $b} ]
    }

    proc pow_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]

        rule "$a $b" "pow_float_body $c $a $b" name "pow-$a-$b"
    }
    proc pow_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        set c_value [ expr {$a_value ** $b_value} ]
        log "pow_float: $a_value ** $b_value => $c_value"
        store_float $c $c_value
    }

    # checks to see if float i is NaN, sets o to true or false accordingly
    proc is_nan { o i } {
        rule $i "is_nan_body $o $i" name "is_nan-$o-$i"
    }
    proc is_nan_body { o i } {
      set i_value [ retrieve_decr_float $i ]
      # NaN is the only floating point value not equal to itself
      store_integer $o [ expr {$i_value != $i_value} ]
    }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
