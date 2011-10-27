
namespace eval turbine {

    # User functions
    namespace export arithmetic literal shell

    # System functions
    namespace export stack_lookup

    # This is a Swift-1 function
    namespace export plus

    # Bring in Turbine extension features
    namespace import c::new c::rule c::rule_new c::typeof
    namespace import c::insert c::log

    # Called by turbine::init to setup Turbine's argv
    proc argv_init { } {

        global argv
        global null
        global argc
        global argv

        set null 0
        set argc 0
        set argv [ dict create ]
        foreach arg $argv {
            set tokens [ split $arg = ]
            set key [ lindex $tokens 0 ]
            if { [ string index $key 0 ] == "-" } {
                set key [ string range $key 1 end ]
            }
            if { [ string index $key 0 ] == "-" } {
                set key [ string range $key 1 end ]
            }
            set value [ lindex $tokens 1 ]
            set v [ new ]
            string_init $v
            string_set $v $value
            dict set argv $key $value
            debug "argv: $key=<$v>=$value"
            incr argc
        }
    }

    # User function
    # usage: argv_get <result> <optional:default> <key>
    proc argv_get { args } {

        set result [ lindex $args 0 ]
        set key    [ lindex $args 1 ]
        set base ""
        if { [ llength $args ] == 3 }  {
            set base [ lindex $args 2 ]
        }

        set rule_id [ new ]
        rule $rule_id "argv_get-$rule_id" $key $result \
            "tp: argv_get_body $key $base $result"
    }

    # usage: argv_get <optional:default> <key> <result>
    proc argv_get_body { args } {

        global null
        global argv

        set argc [ llength $args ]
        if { $argc != 2 && $argc != 3 } error

        if { $argc == 2 } {
            set base ""
            set result [ lindex $args 1 ]
        } elseif { $argc == 3 } {
            set base [ lindex $args 1 ]
            set result [ lindex $args 2 ]
        }
        set key [ lindex $args 0 ]

        set t [ string_get $key ]
        if { [ catch { set v [ dict get $argv $t ] } ] } {
            string_set $result ""
            return
        }
        string_set $result $v
    }

    # User function
    # This name conflicts with a TCL built-in - it cannot be exported
    # TODO: Replace this with tracef()
    proc trace { args } {

        set rule_id [ rule_new ]
        rule $rule_id "trace-$rule_id" $args { } \
            "tc: turbine::trace_body $args"
    }

    proc trace_body { args } {

        puts -nonewline "trace: "
        set n [ llength $args ]
        for { set i 0 } { $i < $n } { incr i } {
            set v [ lindex $args $i ]
            switch [ typeof $v ] {
                integer {
                    set value [ integer_get $v ]
                    puts -nonewline $value
                }
                string {
                    set value [ string_get $v ]
                    puts -nonewline $value
                }
            }
            if { $i < $n-1 } { puts -nonewline "," }
        }
        puts ""
    }

    # User function
    proc range { result start end } {

        set rule_id [ rule_new ]
        rule $rule_id "range-$rule_id" "$start $end" $result \
            "tp: range_body $result $start $end"
    }

    proc range_body { result start end } {

        set start_value [ integer_get $start ]
        set end_value   [ integer_get $end ]

        range_work $result $start_value $end_value
    }

    proc range_work { result start end } {
        set k 0
        for { set i $start } { $i <= $end } { incr i } {
            set td [ data_new ]
            integer_init $td
            integer_set $td $i
            container_insert $result $k $td
            incr k
        }
        close_container $result
    }

    # User function
    # Construct a distributed container of sequential integers
    proc drange { result start end parts } {

        set rule_id [ rule_new ]
        rule $rule_id "drange-$rule_id" "$start $end" $result \
            "tp: drange_body $result $start $end $parts"
    }

    proc drange_body { result start end parts } {

        set start_value [ integer_get $start ]
        set end_value   [ integer_get $end ]
        set parts_value [ integer_get $parts ]
        set size        [ expr $end_value - $start_value + 1]
        set step        [ expr $size / $parts_value ]

        global WORK_TYPE
        for { set i 0 } { $i < $parts_value } { incr i } {
            # top-level container
            set td [ data_new ]
            container_init $td integer
            container_insert $result $i $td
            # start
            set s [ expr $i *  $step ]
            # end
            set e [ expr $s + $step - 1 ]
            adlb::put $adlb::ANY $WORK_TYPE(CONTROL) \
                "procedure tp: range_work $td $s $e"
        }
        close_container $result
    }

    # User function
    # Loop over a distributed container
    proc dloop { loop_body stack container } {

        c::log "log_dloop:"
        set rule_id [ rule_new ]
        rule $rule_id "dloop-$rule_id" $container "" \
            "tp: dloop_body $loop_body $stack $container"
    }

    proc dloop_body { loop_body stack container } {

        set keys [ container_list $container ]

        global WORK_TYPE
        foreach key $keys {
            c::log "log_dloop_body"
            set c [ container_get $container $key ]
            release _ "tp: loop_body $loop_body $stack $c"
        }
    }

    # User function
    # NOT TESTED
    proc enumerate { result container } {

        set rule_id [ data_new ]
        rule $rule_id "enumerate-$rule_id" $container $result \
            "tp: enumerate_body $result $container"
    }

    proc enumerate_body { result container } {

        set s [ container_list $container ]
        string_set $result $s
    }

    # User function
    proc readdata { result filename } {

        set rule_id [ data_new ]
        rule $rule_id "read_data-$rule_id" $filename $result  \
            "tp: readdata_body $result $filename"
    }

    proc readdata_body { result filename } {

        set name_value [ string_get $filename ]
        if { [ catch { set fd [ open $name_value r ] } e ] } {
            error "Could not open file: '$name_value'"
        }

        set i 0
        while { [ gets $fd line ] >= 0 } {
            set s [ data_new ]
            string_init $s
            string_set $s $line
            container_insert $result $i $s
            incr i
        }
        close_container $result
    }

    # User function
    proc loop { stmts stack container } {
        set rule_id [ rule_new ]
        rule $rule_id "loop-$rule_id" $container {} \
            "tp: loop_body $stmts $stack $container"
    }

    proc loop_body { stmts stack container } {
        set type [ container_typeof $container ]
        set L    [ container_list $container ]
        # puts "container_got: $type: $L"
        c::log "log_loop_body start"
        foreach subscript $L {
            set td_key [ literal $type $subscript ]
            # Call user body with subscript as TD
            $stmts $stack $container $td_key
        }
        c::log "log_loop_body done"
    }

    # Utility function to set up a TD
    proc literal { type value } {

        set result [ data_new ]
        ${type}_init $result
        ${type}_set $result $value
        return $result
    }

    # Copy from TD src to TD dest
    # src must be closed
    # dest must be a new TD but not created or closed
    # NOT TESTED
    proc copy { src dest } {

        set type [ typeof $src ]
        switch $type {
            integer {
                set t [ integer_get $src ]
                integer $dest
                integer_set $dest $t
            }
            string {
                set t [ string_get $src ]
                string_init $dest
                string_set $dest $t
            }
        }
    }

    # User function
    # usage: strcat <result> <args>*
    proc strcat { args } {

        set result [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]

        set rule_id [ new ]
        rule $rule_id "strcat-$rule_id" $inputs $result \
            "tp: strcat_body $inputs $result"
    }

    # usage: strcat_body <args>* <result>
    proc strcat_body { args } {

        set result [ lindex $args end ]
        set inputs [ lreplace $args end end ]

        set output [ list ]
        foreach input $inputs {
            set t [ string_get $input ]
            lappend output $t
        }
        set total [ join $output "" ]
        string_set $result $total
    }

    # User function
    proc toint { result input } {

        set rule_id [ new ]
        rule $rule_id "toint-$rule_id" $input $result \
            "tp: toint_body $input $result"
    }

    proc toint_body { input result } {

        set t [ string_get $input ]
        # TCL performs the conversion naturally
        integer_set $result $t
    }

    # usage: arithmetic_body <result> <expr> <args>*
    # example: assume td1 = 5, td2 = 6, td3 = 7
    # arithmetic td4 "(_+_)*_" td1 td2 td3
    # results in td4=210
    proc arithmetic { args } {

        set result     [ lindex $args 0 ]
        set expression [ lindex $args 1 ]
        set inputs     [ lreplace $args 0 1 ]

        set rule_id [ rule_new ]
        rule $rule_id "arithmetic-$rule_id" $inputs $result \
            "tp: arithmetic_body $inputs $expression $result"
    }

    # usage: arithmetic_body <args>* <expr> <result>
    proc arithmetic_body { args } {

        set expression [ lindex $args end-1 ]
        set result [ lindex $args end ]
        set inputs [ lreplace $args end-1 end ]
        set count [ llength $inputs ]

        set working $expression
        for { set i 0 } { $i < $count } { incr i } {
            set td [ lindex $inputs $i ]
            set v [ integer_get $td ]
            regsub "_" $working $v working
        }

        set total [ expr $working ]
        integer_set $result $total
    }

    # This is a Swift-1 function
    # c = a+b;
    # and sleeps for c seconds
    proc plus { c a b } {
        set rule_id [ rule_new ]
        rule $rule_id "plus-$a-$b" "$a $b" $c \
            "tf: plus_body $c $a $b"
    }
    proc plus_body {c a b } {
        set a_value [ integer_get $a ]
        set b_value [ integer_get $b ]
        set c_value [ expr $a_value + $b_value ]
        # Emulate some computation time
        log "plus $a_value + $b_value => $c_value"
        exec sleep $c_value
        integer_set $c $c_value
    }

    # Execute shell command
    proc shell { args } {
        puts "turbine::shell $args"
        set command [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]
        set rule_id [ rule_new ]
        rule $rule_id "shell-$command" $inputs "" \
            "tf: shell_body $command \"$inputs\""
    }

    proc shell_body { args } {
        set command [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]
        set values [ list ]
        foreach i $inputs {
            set value [ integer_get $i ]
            lappend values $value
        }
        debug "executing: $command $values"
        exec $command $values
    }

    # Look in all enclosing stack frames for the TD for the given symbol
    # If not found, return the empty string
    proc stack_lookup { stack symbol } {

        set result ""
        while true {
            set result [ container_get $stack $symbol ]
            if { [ string equal $result "0" ] } {
                # Not found in local stack frame: check enclosing frame
                set enclosure [ container_get $stack _enclosure ]
                if { ! [ string equal $enclosure "0" ] } {
                    set stack $enclosure
                } else {
                    # We have no more frames to check
                    break
                }

            } else {
                return $result
            }
        }
        return ""
    }
}
