
proc turbine_trace { args } {

    set rule_id [ turbine_new ]
    turbine_rule $rule_id "trace-$rule_id" $args { } \
        "tp: turbine_trace_body $args"
}

proc turbine_trace_body { args } {

    set n [ llength $args ]
    for { set i 0 } { $i < $n } { incr i } {
        set v [ lindex $args $i ]
        switch [ turbine_typeof $v ] {
            integer {
                set value [ turbine_integer_get $v ]
                puts -nonewline $value
            }
            string {
                set value [ turbine_string_get $v ]
                puts -nonewline $value
            }
        }
        if { $i < $n-1 } { puts -nonewline "," }
    }
    puts ""
}

proc turbine_range { result start end } {

    set rule_id [ turbine_new ]
    turbine_rule $rule_id "range-$rule_id" "$start $end" $result \
        "tp: turbine_range_body $result $start $end"
}

proc turbine_range_body { result start end } {

    set start_value [ turbine_integer_get $start ]
    set end_value   [ turbine_integer_get $end ]

    set k 0
    for { set i $start } { $i <= $end_value } { incr i } {
        set td [ turbine_new ]
        turbine_integer $td
        turbine_integer_set $td $i
        turbine_insert $result key $k $td
        incr k
    }
}

proc turbine_enumerate { result container } {

    set rule_id [ turbine_new ]
    turbine_rule $rule_id "enumerate-$rule_id" $container $result \
        "tp: turbine_enumerate_body $result $container"
}

proc turbine_enumerate_body { result container } {

    set s [ turbine_container_get $container ]
    turbine_string_set $result $s
}

proc turbine_readdata { result filename } {

    set rule_id [ turbine_new ]
    turbine_rule $rule_id "read_data-$rule_id" $filename $result  \
        "tp: turbine_readdata_body $result $filename"
}

proc turbine_readdata_body { result filename } {

    set name_value [ turbine_string_get $filename ]
    if { [ catch { set fd [ open $name_value r ] } e ] } {
        error "Could not open file: $name_value"
    }

    set i 0
    while { [ gets $fd line ] >= 0 } {
        set s [ turbine_new ]
        turbine_string $s
        turbine_string_set $s $line
        turbine_insert $result key $i $s
        incr i
    }
}

proc turbine_loop { stmts container } {
    set rule_id [ turbine_new ]
    turbine_rule $rule_id "loop-$rule_id" $container {} \
        "tp: turbine_loop_body $stmts $container"
}

proc turbine_loop_body { stmts container } {
    set s [ turbine_container_get $container ]
    puts "container_got: $s"
    foreach t $s {
        set i [ turbine_new ]
        turbine_integer $i
        turbine_integer_set $i $t
        $stmts $i
    }
}
