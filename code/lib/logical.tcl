
# Turbine builtin function for logical boolean expressions,
# including boolean ops like or, along with relational ops like
# equals and greater than
# We follow the calling conventions for Turbine built-ins
#
# There is a lot of redundancy in the definitions of the functions between
# different Turbine types, but it is difficult in TCL to write polymorphic
# code without doing funny things that hurt the ability of TCL to compile to
# good bytecode like constructing function names at run.  Hopefully these
# definitions will be fairly stable so the redundancy won't be a major issue.

namespace eval turbine {
    namespace export and or not
    namespace export eq_integer neq_integer lt_integer gt_integer lte_integer \
                                                                  gte_integer
    namespace export eq_float neq_float lt_float gt_float lte_float gte_float

    # This is a Swift-2 function
    # o = ! i;
    proc not { parent o i } {
        set rule_id [ rule_new ]
        rule $rule_id "not-$i" $i $o \
            "tf: not_body $o $i"
    }
    proc not_body { o i } {
        set i_value [ get $i ]
        set o_value [ expr ! $i_value ]
        log "not $i_value => $o_value"
        set_integer $o $o_value
    }

    # This is a Swift-2 function
    # c = a && b;
    proc and { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "and-$a-$b" "$a $b" $c \
            "tf: and_body $c $a $b"
    }
    proc and_body { c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value && $b_value ]
        # Emulate some computation time
        log "and: $a_value && $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }

    # This is a Swift-2 function
    # c = a || b;
    proc or { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "or-$a-$b" "$a $b" $c \
            "tf: or_body $c $a $b"
    }
    proc or_body { c a b } {
        set a_value [ get_integer $a ]
        set b_value [ get_integer $b ]
        set c_value [ expr $a_value || $b_value ]
        # Emulate some computation time
        log "or: $a_value || $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc eq_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "eq_integer-$a-$b" "$a $b" $c \
            "tf: eq_integer_body $c $a $b"
    }
    proc eq_integer_body { c a b } {
        set a_value [ get_integer $a ]
        set b_value [ get_integer $b ]
        if { [ expr $a_value == $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "eq_integer $a_value == $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc neq_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "neq_integer-$a-$b" "$a $b" $c \
            "tf: neq_integer_body $c $a $b"
    }
    proc neq_integer_body { c a b } {
        set a_value [ get_integer $a ]
        set b_value [ get_integer $b ]
        if { [ expr $a_value != $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "neq_integer $a_value == $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc lt_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "lt_integer-$a-$b" "$a $b" $c \
            "tf: lt_integer_body $c $a $b"
    }
    proc lt_integer_body { c a b } {
        set a_value [ get_integer $a ]
        set b_value [ get_integer $b ]
        if { [ expr $a_value < $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "lt_integer $a_value < $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc lte_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "lte_integer-$a-$b" "$a $b" $c \
            "tf: lte_integer_body $c $a $b"
    }
    proc lte_integer_body { c a b } {
        set a_value [ get_integer $a ]
        set b_value [ get_integer $b ]
        if { [ expr $a_value <= $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "lte_integer $a_value <= $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }

    proc gt_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "gt_integer-$a-$b" "$a $b" $c \
            "tf: gt_integer_body $c $a $b"
    }
    proc gt_integer_body { c a b } {
        set a_value [ get_integer $a ]
        set b_value [ get_integer $b ]
        if { [ expr $a_value > $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "gt_integer $a_value > $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc gte_integer { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "gte_integer-$a-$b" "$a $b" $c \
            "tf: gte_integer_body $c $a $b"
    }
    proc gte_integer_body { c a b } {
        set a_value [ get_integer $a ]
        set b_value [ get_integer $b ]
        if { [ expr $a_value >= $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "gte_integer $a_value >= $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc eq_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "eq_float-$a-$b" "$a $b" $c \
            "tf: eq_float_body $c $a $b"
    }
    proc eq_float_body { c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        if { [ expr $a_value == $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "eq_float $a_value == $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc neq_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "neq_float-$a-$b" "$a $b" $c \
            "tf: neq_float_body $c $a $b"
    }
    proc neq_float_body { c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        if { [ expr $a_value != $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "neq_float $a_value == $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc lt_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "lt_float-$a-$b" "$a $b" $c \
            "tf: lt_float_body $c $a $b"
    }
    proc lt_float_body { c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        if { [ expr $a_value < $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "lt_float $a_value < $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc lte_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "lte_float-$a-$b" "$a $b" $c \
            "tf: lte_float_body $c $a $b"
    }
    proc lte_float_body { c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        if { [ expr $a_value <= $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "lte_float $a_value <= $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }

    proc gt_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "gt_float-$a-$b" "$a $b" $c \
            "tf: gt_float_body $c $a $b"
    }
    proc gt_float_body { c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        if { [ expr $a_value > $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "gt_float $a_value > $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc gte_float { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "gte_float-$a-$b" "$a $b" $c \
            "tf: gte_float_body $c $a $b"
    }
    proc gte_float_body { c a b } {
        set a_value [ get_float $a ]
        set b_value [ get_float $b ]
        if { [ expr $a_value >= $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "gte_float $a_value >= $b_value => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc eq_string { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "eq_string-$a-$b" "$a $b" $c \
            "tf: eq_string_body $c $a $b"
    }
    proc eq_string_body { c a b } {
        set a_value [ get_string $a ]
        set b_value [ get_string $b ]
        if {[ string equal $a_value $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "eq_string \"$a_value\" == \"$b_value\" => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
    
    proc neq_string { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "neq_string-$a-$b" "$a $b" $c \
            "tf: neq_string_body $c $a $b"
    }
    proc neq_string_body { c a b } {
        set a_value [ get_string $a ]
        set b_value [ get_string $b ]
        if {! [string equal $a_value $b_value]} {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "neq_string \"$a_value\" == \"$b_value\" => $c_value"
        # exec sleep $c_value
        set_integer $c $c_value
    }
}
