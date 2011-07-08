
proc turbine_trace { vars } {

    set rule_id [ turbine_new ]
    turbine_rule $rule_id $rule_id $vars { } "
        tp: turbine_trace_body $vars
    "
}

proc turbine_trace_body { vars } {

    foreach v $vars {
        switch [ turbine_typeof $v ] {
            integer {
                set value [ turbine_integer_get $v ]
                puts -nonewline $value
            }
        }
    }
    puts ""
}
