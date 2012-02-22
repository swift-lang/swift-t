# Turbine builtin assert functions for debugging

namespace eval turbine {

    # assert(cond, msg)
    # If cond is 0, then terminate program, printing msg
    proc assert { stack noresult inputs } {
        set cond [ lindex $inputs 0 ]
        set msg [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "assert-$cond-$msg" "$cond $msg" "" \
            "tf: assert_body $cond $msg"
    }
    
    proc assert_body { cond msg } {
        set cond_value [ get $cond ]
        if { $cond_value == 0 } {
            set msg_value [ get $msg ]
            error "Assertion failed!: $msg_value"
        } else {
            log "assert: $cond_value"
        }
    }
    
    # assert(arg1, arg2, msg)
    # if arg1 != arg2 according to TCL's comparison rules
    #  (e.g. "1" == 1 and 1 == 1)
    # then crash, printing the two values and the provided msg
    proc assertEqual { stack noresult inputs } {
        set arg1 [ lindex $inputs 0 ]
        set arg2 [ lindex $inputs 1 ]
        set msg [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "assertEqual-$arg1-$arg2-$msg" "$arg1 $arg2 $msg" "" \
            "tf: assertEqual_body $arg1 $arg2 $msg"
    }
    
    proc assert_body { arg1 arg2 msg } {
        set arg1_value [ get $arg1 ]
        set arg2_value [ get $arg2 ]
        if { $arg1_value != $arg2_value } {
            set msg_value [ get $msg ]
            error "Assertion failed $arg1_value != $arg2_value: $msg_value"
        } else {
            log "assertEqual: $arg1_value $arg2_value"
        }
    }
}
