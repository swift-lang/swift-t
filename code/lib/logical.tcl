
# Turbine builtin function for logical boolean expressions,
# including boolean ops like or, along with relational ops like
# equals and greater than
# We follow the calling conventions for Turbine built-ints

namespace eval turbine {
    namespace export and or not

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

    #TODO: 
    # eq_string neq_string
    # lt_integer gt_integer lte_integer gte_integer
    # eq_float neq_float lt_float gt_float lte_float gte_float
    
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
}
