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

# Turbine builtin functions for logical boolean expressions, including
# boolean ops like or, along with relational ops like equals and
# greater than. We follow the calling conventions for Turbine
# built-ins
#
# There is a lot of redundancy in the definitions of the functions
# between different Turbine types, but it is difficult in Tcl to write
# polymorphic code without doing funny things that hurt the ability of
# Tcl to compile to good bytecode like constructing function names at
# run time.  These definitions will be fairly stable so the redundancy
# won't be a major issue.

namespace eval turbine {
    namespace export and or not
    namespace export eq_integer  neq_integer \
                     lt_integer  gt_integer  \
                     lte_integer gte_integer
    namespace export eq_float  neq_float \
                     lt_float  gt_float  \
                     lte_float gte_float

    # This is a Swift-2 function
    # o = ! i;
    proc not { o i } {
        rule $i "not_body $o $i" name "not-$o-$i" 
    }
    proc not_body { o i } {
        set i_value [ retrieve_decr_integer $i ]
        set o_value [ expr {! $i_value} ]
        log "not $i_value => $o_value"
        store_integer $o $o_value
    }

    # This is a Swift-2 function
    # c = a && b;
    proc and { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "and_body $c $a $b" name "and-$c-$a-$b" 
    }
    proc and_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        set c_value [ expr {$a_value && $b_value} ]
        # Emulate some computation time
        log "and: $a_value && $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    # This is a Swift-2 function
    # c = a || b;
    proc or { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "or_body $c $a $b" name "or-$c-$a-$b" 
    }
    proc or_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        set c_value [ expr {$a_value || $b_value} ]
        # Emulate some computation time
        log "or: $a_value || $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc eq_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "eq_integer_body $c $a $b" \
            name "eq_integer-$a-$b" 
    }
    proc eq_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        if { [ expr {$a_value == $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "eq_integer $a_value == $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc neq_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "neq_integer_body $c $a $b" \
            name "neq_integer-$c-$a-$b" 
    }
    proc neq_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        if { [ expr {$a_value != $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "neq_integer $a_value == $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc lt_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "lt_integer_body $c $a $b" \
            name "lt_integer-$c-$a-$b" 
    }
    proc lt_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        if { [ expr {$a_value < $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "lt_integer $a_value < $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc lte_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "lte_integer_body $c $a $b" \
            name "lte_integer-$c-$a-$b" 
    }
    proc lte_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        if { [ expr {$a_value <= $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "lte_integer $a_value <= $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc gt_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "gt_integer_body $c $a $b" \
            name "gt_integer-$c-$a-$b" 
    }
    proc gt_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        if { [ expr {$a_value > $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "gt_integer $a_value > $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc gte_integer { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "gte_integer_body $c $a $b" \
          name "gte_integer-$c-$a-$b" 
    }
    proc gte_integer_body { c a b } {
        set a_value [ retrieve_decr_integer $a ]
        set b_value [ retrieve_decr_integer $b ]
        if { [ expr {$a_value >= $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "gte_integer $a_value >= $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc eq_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "eq_float_body $c $a $b" \
            name "eq_float-$c-$a-$b" 
    }
    proc eq_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        if { [ expr {$a_value == $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "eq_float $a_value == $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc neq_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "neq_float_body $c $a $b" \
            name "neq_float-$c-$a-$b" 
    }
    proc neq_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        if { [ expr {$a_value != $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "neq_float $a_value == $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc lt_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "lt_float_body $c $a $b" \
            name "lt_float-$a-$b" 
    }
    proc lt_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        if { [ expr {$a_value < $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "lt_float $a_value < $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc lte_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "lte_float_body $c $a $b" \
            name "lte_float-$c-$a-$b" 
    }
    proc lte_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        if { [ expr {$a_value <= $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "lte_float $a_value <= $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc gt_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "gt_float_body $c $a $b" name "gt_float-$a-$b" 
    }
    proc gt_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        if { [ expr {$a_value > $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "gt_float $a_value > $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc gte_float { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "gte_float_body $c $a $b" \
            name "gte_float-$a-$b" 
    }
    proc gte_float_body { c a b } {
        set a_value [ retrieve_decr_float $a ]
        set b_value [ retrieve_decr_float $b ]
        if { [ expr {$a_value >= $b_value} ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "gte_float $a_value >= $b_value => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc eq_string { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" \
            "eq_string_body $c $a $b" name "eq_string-$a-$b" 
    }
    proc eq_string_body { c a b } {
        set a_value [ retrieve_decr_string $a ]
        set b_value [ retrieve_decr_string $b ]
        if {[ string equal $a_value $b_value ] } {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "eq_string \"$a_value\" == \"$b_value\" => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }

    proc neq_string { c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        rule "$a $b" "neq_string_body $c $a $b" \
            name "neq_string-$a-$b" 
    }
    proc neq_string_body { c a b } {
        set a_value [ retrieve_decr_string $a ]
        set b_value [ retrieve_decr_string $b ]
        if {! [string equal $a_value $b_value]} {
            set c_value 1
        } else {
            set c_value 0
        }
        # Emulate some computation time
        log "neq_string \"$a_value\" == \"$b_value\" => $c_value"
        # exec sleep $c_value
        store_integer $c $c_value
    }
}
