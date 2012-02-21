
# Turbine builtin functions

# All builtins will have signature:
#   f <STACK> <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs
# even if some of the arguments are not used
# The uniformity allows the stp code generator to simply write all
# calls to builtins the same way
# (Not all functions conform to this but they will. -Justin)

namespace eval turbine {

    # User functions
    namespace export arithmetic enumerate literal shell

    # Memory functions (will be in turbine::f namespace)
    namespace export container_f_get container_f_insert
    namespace export f_reference f_dereference
    namespace export f_container_create_nested

    # System functions
    namespace export stack_lookup

    # This is a Swift-1 function
    namespace export plus

    # These are Swift-2 functions
    namespace export minus copy not set1

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

        set rule_id [ rule_new ]
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

    proc call_composite { stack f outputs inputs } {

        set rule_id [ rule_new ]
        turbine::c::rule $rule_id $f "$inputs" "$outputs" \
            "tp: $f $stack $outputs $inputs"
        return $rule_id
    }

    # User function
    # This name conflicts with a TCL built-in - it cannot be exported
    # TODO: Replace this with tracef()
    proc trace { args } {

        # parent stack and output arguments not read
        set tds   [ lindex $args 2 ]
        if { ! [ string length $tds ] } {
            error "trace: received no arguments!"
        }
        set rule_id [ rule_new ]
        rule $rule_id "trace-$rule_id" $tds { } \
            "tc: turbine::trace_body $tds"
    }

    proc trace_body { args } {

        puts -nonewline "trace: "
        set n [ llength $args ]
        for { set i 0 } { $i < $n } { incr i } {
            set v [ lindex $args $i ]
            switch [ adlb::typeof $v ] {
                integer {
                    set value [ get $v ]
                }
                string {
                    set value [ get $v ]
                }
                float {
                    set value [ get $v ]
                }
            }
            puts -nonewline $value
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

        set start_value [ get $start ]
        set end_value   [ get $end ]

        range_work $result $start_value $end_value
    }

    proc range_work { result start end } {
        set k 0
        for { set i $start } { $i <= $end } { incr i } {
            allocate td integer
            set_integer $td $i
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

        set start_value [ get $start ]
        set end_value   [ get $end ]
        set parts_value [ get $parts ]
        set size        [ expr $end_value - $start_value + 1]
        set step        [ expr $size / $parts_value ]

        global WORK_TYPE
        for { set i 0 } { $i < $parts_value } { incr i } {
            # top-level container
            allocate_container c integer
            container_insert $result $i $c
            # start
            set s [ expr $i *  $step ]
            # end
            set e [ expr $s + $step - 1 ]
            adlb::put $adlb::ANY $WORK_TYPE(CONTROL) \
                "procedure tp: range_work $c $s $e"
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

    # Sum all of the values in a container of integers
    # inputs: [ list c r ]
    # c: the container
    # r: the turbine id to store the sum into
    proc sum_integer { stack outputs inputs } {
        set container [ lindex $inputs 0 ]
        set result [ lindex $outputs 0 ]
        set rule_id [ rule_new ]
        rule $rule_id "sum-$rule_id" $container "" \
            "tp: sum_integer_body $stack $container $result 0 0"
    }

    proc sum_integer_body { stack container result accum next_index } {
        set keys [ container_list $container ]
        # TODO: could divide and conquer instead of
        #       doing linear search
        set n [ llength $keys ]
        set i $next_index
        while { $i < $n } {
            set key [ lindex $keys $i ]
            set turbine_id [ container_get $container $key ]

            if { [ catch { set val [ get $turbine_id ] } ] == 0 } {
                # add to the sum
                set accum [ expr $accum + $val ]
                incr i
            } else {
                # block until the next turbine id is finished,
                #   then continue running
                set rule_id [ rule_new ]
                rule $rule_id "sum-$rule_id" $turbine_id "" \
                    "tp: sum_integer_body $stack $container $result $accum $i"
                # return immediately without setting result
                return
            }
        }
        # If we get out of loop, we're done
        set_integer $result $accum
    }

    # When container is closed, concatenate its keys in result
    # container: The container to read
    # result: An initialized string
    proc enumerate { stack result container } {
        set rule_id [ rule_new ]
        rule $rule_id "enumerate-$container" $container $result \
            "tp: enumerate_body $result $container"
    }

    proc enumerate_body { result container } {
        set s [ container_list $container ]
        set_string $result $s
    }

    # User function
    proc readdata { result filename } {

        set rule_id [ rule_new ]
        rule $rule_id "read_data-$rule_id" $filename $result  \
            "tp: readdata_body $result $filename"
    }

    proc readdata_body { result filename } {

        set name_value [ get $filename ]
        if { [ catch { set fd [ open $name_value r ] } e ] } {
            error "Could not open file: '$name_value'"
        }

        set i 0
        while { [ gets $fd line ] >= 0 } {
            allocate s string
            set_string $s $line
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
    # usage: [<name>] <type> <value>
    # If name is given, store TD in variable name and log name
    proc literal { args } {

        if { [ llength $args ] == 2 } {
            set type   [ lindex $args 0 ]
            set value  [ lindex $args 1 ]
            set result [ allocate $type ]
        } elseif { [ llength $args ] == 3 } {
            set name   [ lindex $args 0 ]
            set type   [ lindex $args 1 ]
            set value  [ lindex $args 2 ]
            puts "name: $name"
            set result [ allocate $name $type ]
            upvar 1 $name n
            set n $result
        } else {
            error "turbine::literal requires 2 or 3 args!"
        }

        set_${type} $result $value

        return $result
    }

    # Copy from TD src to TD dest
    # src must be closed
    # dest must be a new TD but not created or closed
    # DELETE THIS
    # proc copy { src dest } {

    #     set type [ typeof $src ]
    #     switch $type {
    #         integer {
    #             set t [ get $src ]
    #             integer $dest
    #             set_integer $dest $t
    #         }
    #         string {
    #             set t [ string_get $src ]
    #             string_init $dest
    #             string_set $dest $t
    #         }
    #     }
    # }

    # User function
    # usage: strcat <result> <args>*
    proc strcat { args } {

        set result [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]

        set rule_id [ rule_new ]
        rule $rule_id "strcat-$rule_id" $inputs $result \
            "tp: strcat_body $inputs $result"
    }

    # usage: strcat_body <args>* <result>
    proc strcat_body { args } {

        set result [ lindex $args end ]
        set inputs [ lreplace $args end end ]

        set output [ list ]
        foreach input $inputs {
            set t [ get $input ]
            lappend output $t
        }
        set total [ join $output "" ]
        set_string $result $total
    }

    proc substring { stack result inputs  } {
        set rule_id [ rule_new ]
        set str [ lindex $inputs 0 ]
        set first [ lindex $inputs 1 ]
        set len [ lindex $inputs 2 ]
        rule $rule_id "substring-$rule_id-$str-$first-$len" $inputs $result \
            "tp: substring_body $result $str $first $len"
    }

    proc substring_body { result str first len } {
        set str_val   [ get $str ]
        set first_val [ get $first ]
        set len_val   [ get $len ]

        set last [ expr $first_val + $len_val - 1 ]
        set result_val [ string range $str_val $first_val $last ]
        set_string $result $result_val
    }

    # User function
    proc toint { stack result input } {
        set rule_id [ rule_new ]
        rule $rule_id "toint-$rule_id" $input $result \
            "tp: toint_body $input $result"
    }

    proc toint_body { input result } {

        set t [ get $input ]
        # Tcl performs the conversion naturally
        set_integer $result $t
    }

    proc fromint { stack result input } {
        set rule_id [ rule_new ]
        rule $rule_id "fromint-$rule_id" $input $result \
            "tp: fromint_body $input $result"
    }

    proc fromint_body { input result } {
        set t [ get $input ]
        # Tcl performs the conversion naturally
        set_string $result $t
    }

    # OBSOLETE: The parser can generate code as efficient as this
    # usage: arithmetic <parent> <result> [ <expr> <args>* ]
    # example: assume td1 = 5, td2 = 6, td3 = 7
    # arithmetic td4 "(_+_)*_" td1 td2 td3
    # results in td4=210
    proc arithmetic { args } {

        # parent stack argument not read
        set result     [ lindex $args 1 ]
        set inputs     [ lindex $args 2 ]
        set expression [ lindex $inputs 0 ]
        set tds        [ lreplace $inputs 0 0 ]

        set rule_id [ rule_new ]
        rule $rule_id "arithmetic-$rule_id" $tds $result \
            "tp: arithmetic_body $tds $expression $result"
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
            set v [ get $td ]
            regsub "_" $working $v working
        }

        set total [ expr $working ]
        set_integer $result $total
    }

    proc plus { type parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "plus-$a-$b" "$a $b" $c \
            "tl: plus_body $type $parent $c $a $b"
    }

    proc plus_body { type parent c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value + $b_value ]
        log "plus: $a_value + $b_value => $c_value"
        set_${type} $c $c_value
    }

    proc minus { type parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "minus-$a-$b" "$a $b" $c \
            "tl: minus_body $type $parent $c $a $b"
    }

    proc minus_body { type parent c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value - $b_value ]
        log "minus: $a_value - $b_value => $c_value"
        set_${type} $c $c_value
    }

    # c = a*b;
    proc multiply { type parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "mult-$a-$b" "$a $b" $c \
            "tf: multiply_body $type $c $a $b"
    }
    proc multiply_body { type c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value * $b_value ]
        log "multiply: $a_value * $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        set_${type} $c $c_value
    }

    # c = a/b
    proc divide { type parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "div-$a-$b" "$a $b" $c \
            "tf: divide_body $type $c $a $b"
    }
    proc divide_body { type c a b } {
        set a_value [ get $a ]
        set b_value [ get $b ]
        set c_value [ expr $a_value / $b_value ]
        log "divide: $a_value / $b_value => $c_value"
        set_${type} $c $c_value
    }

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
        set a_value [ float_get $a ]
        set b_value [ float_get $b ]
        set c_value [ expr $a_value + $b_value ]
        log "plus: $a_value + $b_value => $c_value"
        float_set $c $c_value
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
        set a_value [ float_get $a ]
        set b_value [ float_get $b ]
        set c_value [ expr $a_value - $b_value ]
        log "minus: $a_value - $b_value => $c_value"
        float_set $c $c_value
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
        set a_value [ float_get $a ]
        set b_value [ float_get $b ]
        set c_value [ expr $a_value * $b_value ]
        log "multiply: $a_value * $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        float_set $c $c_value
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
        set a_value [ integer_get $a ]
        set b_value [ integer_get $b ]
        set c_value [ expr $a_value / $b_value ]
        log "divide: $a_value / $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        integer_set $c $c_value
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
        set a_value [ float_get $a ]
        set b_value [ float_get $b ]
        set c_value [ expr $a_value / $b_value ]
        log "divide: $a_value / $b_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        float_set $c $c_value
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
        set a_value [ integer_get $a ]
        set b_value [ integer_get $b ]
        set c_value [ expr $a_value || $b_value ]
        # Emulate some computation time
        log "or: $a_value || $b_value => $c_value"
        # exec sleep $c_value
        integer_set $c $c_value
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
        set i_value [ float_get $i ]
        log "copy $i_value => $i_value"
        float_set $o $i_value
    }

    # o = i;
    proc copy_string { parent o i } {
        set rule_id [ rule_new ]
        rule $rule_id "copystring-$o-$i" $i $o \
            "tl: copy_string_body $o $i"
    }
    proc copy_string_body { o i } {
        set i_value [ string_get $i ]
        log "copy $i_value => $i_value"
        string_set $o $i_value
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
        set a_value [ float_get $a ]
        set c_value [ expr 0 - $a_value ]
        log "negate: -1 * $a_value => $c_value"
        # Emulate some computation time
        # exec sleep $c_value
        float_set $c $c_value
    }

    # Good for performance testing
    # c = 1;
    # and sleeps
    proc set0 { parent c } {

        set rule_id [ rule_new ]
        rule $rule_id "set1-$" "" $c \
            "tf: set0_body $parent $c"
    }
    proc set0_body { parent c } {
        log "set0"

        variable stats
        dict incr stats set0

        # Emulate some computation time
        # after 1000
        set_integer $c 0
    }

    # Good for performance testing
    # c = 1;
    # and sleeps
    proc set1 { parent c } {

        set rule_id [ rule_new ]
        rule $rule_id "set1-$" "" $c \
            "tf: set1_body $parent $c"
    }
    proc set1_body { parent c } {
        log "set1"

        variable stats
        dict incr stats set1

        # Emulate some computation time
        # after 1000
        set_integer $c 1
    }

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
            set value [ get $i ]
            lappend values $value
        }
        debug "executing: $command $values"
        exec $command $values
    }

    # Look in all enclosing stack frames for the TD for the given symbol
    # If not found, abort
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
        abort "stack_lookup failure: stack: <$stack> symbol: $symbol"
    }

    # When i is closed, set d := c[i]
    # d: the destination, an integer
    # inputs: [ list c i ]
    # c: the container
    # i: the subscript
    proc container_f_get_integer { parent d inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "container_f_get-$c-$i" $i $d \
            "tl: turbine::container_f_get_integer_body $d $c $i"
    }

    proc container_f_get_integer_body { d c i } {
        set t1 [ get $i ]
        set t2 [ container_get $c $t1 ]
        if { $t2 == 0 } {
            error "lookup failed: container_f_get <$c>\[$t1\]"
        }
        set t3 [ get $t2 ]
        set_integer $d $t3
    }

    # When i is closed, set c[i] := d
    # inputs: [ list c i d ]
    # c: the container
    # i: the subscript
    # d: the data
    # outputs: ignored.  To block on this, use turbine::reference
    proc container_f_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        nonempty c i d
        adlb::slot_create $c
        set rule_id [ rule_new ]
        rule $rule_id "container_f_insert-$c-$i" $i "" \
            "tp: turbine::container_f_insert_body $c $i $d"
    }

    proc container_f_insert_body { c i d } {
        set t1 [ get $i ]
        container_insert $c $t1 $d
    }

    # When i and r are closed, set c[i] := *(r)
    # inputs: [ list c i r ]
    # r: a reference to a turbine ID
    #
    proc container_f_deref_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]

        nonempty c i r
        adlb::slot_create $c
        set rule_id [ rule_new ]
        rule $rule_id "container_f_deref_insert-$c-$i" "$i $r" "" \
            "tp: turbine::container_f_deref_insert_body $c $i $r"
    }

    proc container_f_deref_insert_body { c i r } {
        set t1 [ get $i ]
        set d [ get $r ]
        container_insert $c $t1 $d
    }

    # When r is closed, set c[i] := *(r)
    # inputs: [ list c i r ]
    # i: an integer which is the index to insert into
    # r: a reference to a turbine ID
    #
    proc container_deref_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]

        nonempty c i r
        adlb::slot_create $c
        set rule_id [ rule_new ]
        rule $rule_id "container_deref_insert-$c-$i" "$r" "" \
            "tp: turbine::container_deref_insert_body $c $i $r"
    }

    proc container_deref_insert_body { c i r } {
        set d [ get $r ]
        container_insert $c $i $d
    }

    # Immediately insert data into container without affecting open slot count
    # c: the container
    # i: the subscript
    # d: the data
    # outputs: ignored.
    proc container_immediate_insert { c i d } {
        adlb::slot_create $c
        container_insert $c $i $d
    }

    # When i is closed, get a reference on c[i] in TD r
    # Thus, you can block on r and be notified when c[i] exists
    # r is an integer.  The value of r is the TD of c[i]
    # inputs: [ list c i r ]
    # outputs: None.  You can block on d with turbine::dereference
    # c: the container
    # i: the subscript
    # r: the reference TD
    proc f_reference { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]
        # nonempty c i r
        set rule_id [ rule_new ]
        rule $rule_id "f_reference_body-$c-$i" $i "" \
            "tp: turbine::f_reference_body $c $i $r"
    }
    proc f_reference_body { c i r } {
        set t1 [ get $i ]
        adlb::container_reference $c $t1 $r
    }

    # When reference r is closed, store its (integer) value in v
    proc f_dereference_integer { parent v r } {
        set rule_id [ rule_new ]
        rule $rule_id "f_dereference-$v-$r" $r $v \
            "tp: turbine::f_dereference_integer_body $v $r"
    }
    proc f_dereference_integer_body { v r } {
        set t [ get [ get $r ] ]
        set_integer $v $t
    }

    # When reference r is closed, store its (float) value in v
    proc f_dereference_float { parent v r } {
        set rule_id [ rule_new ]
        rule $rule_id "f_dereference-$v-$r" $r $v \
            "tp: turbine::f_dereference_float_body $v $r"
    }

    proc f_dereference_float_body { v r } {
        set t [ get [ get $r ] ]
        set_float $v $t
    }

    # When reference r is closed, store its (string) value in v
    proc f_dereference_string { parent v r } {
        set rule_id [ rule_new ]
        rule $rule_id "f_dereference-$v-$r" $r $v \
            "tp: turbine::f_dereference_string_body $v $r"
    }
    proc f_dereference_string_body { v r } {
        set t [ get [ get $r ] ]
        set_string $v $t
    }

    # When reference cr is closed, store d = (*cr)[i]
    # Blocks on cr
    # inputs: [ list cr i d ]
    #       cr is a reference to a container
    #       i is a literal int
    # outputs: ignored
    proc f_container_reference_lookup_literal { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "f_container_reference_lookup_literal-$cr" "$cr" "" \
            "tp: turbine::f_container_reference_lookup_literal_body $cr $i $d"

    }

    proc f_container_reference_lookup_literal_body { cr i d } {
        # When this procedure is run, cr should be set and
        # i should be the literal index
        set c [ get $cr ]
        adlb::container_reference $c $i $d
    }

    # When reference cr is closed, store d = (*cr)[i]
    # Blocks on cr and i
    # inputs: [ list cr i d ]
    #       cr is a reference to a container
    # outputs: ignored
    proc f_container_reference_lookup { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "f_container_reference_lookup-$cr" "$cr $i" "" \
            "tp: turbine::f_container_reference_lookup_body $cr $i $d"
    }

    proc f_container_reference_lookup_body { cr i d } {
        # When this procedure is run, cr and i should be set
        set c [ get $cr ]
        set t1 [ get $i ]
        adlb::container_reference $c $t1 $d
    }
    # When reference r on c[i] is closed, store c[i][j] = d
    # Blocks on r and j
    # inputs: [ list r j d ]
    # outputs: ignored
    proc f_container_reference_insert { parent outputs inputs } {
        set r [ lindex $inputs 0 ]
        # set c [ lindex $inputs 1 ]
        set j [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "f_container_reference_insert-$r" "$r $j" "" \
            "tp: turbine::f_container_reference_insert_body $r $j $d"
    }
    proc f_container_reference_insert_body { r j d } {
        # s: The subscripted container
        set c [ get $r ]
        set s [ get $j ]
        container_insert $c $s $d
    }


    # Insert c[i][j] = d
    proc f_container_nested_insert { c i j d } {

        set rule_id [ rule_new ]
        rule $rule_id "fcni" "$i $j" "" \
            "tp: f_container_nested_insert_body_1 $c $i $j $d"
    }

    proc f_container_nested_insert_body_1 { c i j d } {

        if [ container_insert_atomic $c $i ] {
            # c[i] does not exist
            set t [ data_new ]
            allocate_container t integer
            container_insert $c $i $t
        } else {
            allocate r integer
            container_reference $r $c $i
            set rule_id [ rule_new ]
            rule $rule_id fcnib "$r" "" \
                "tp: container_nested_insert_body_2 $r $j $d"
        }
    }

    proc f_container_nested_insert_body_2 { r j d } {
        container_insert $r $j $d
    }

    proc imm_container_create_nested { r c i type } {
        debug "container_create_nested: $r $c\[$i\] $type"
        upvar 1 $r v
        allocate v integer
        __container_create_nested $v $c $i $type
    }

    proc __container_create_nested { r c i type } {
        debug "__container_create_nested: $r $c\[$i\] $type"
        if [ adlb::insert_atomic $c $i ] {
            # Member did not exist: create it and get reference
            allocate_container t $type
            adlb::insert $c $i $t
        }

        adlb::container_reference $c $i $r
    }

    proc f_container_create_nested { r c i type } {

        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r

        set rule_id [ rule_new ]
        rule $rule_id fccn "" "$i" \
               "tp: f_container_create_nested_body $tmp_r $c $i $type"
    }


    # Create container at c[i]
    # Set r, a reference TD on c[i]
    proc f_container_create_nested_body { r c i type } {

        debug "f_container_create_nested: $r $c\[$i\] $type"

        set s [ get $i ]
        __container_create_nested $r $c $s $type
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc container_reference_create_nested { r cr i type } {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r

        set rule_id [ rule_new ]
        rule $rule_id fcrcn "" "$cr" \
           "tp: container_reference_create_nested_body $tmp_r $cr $i $type"
    }

    proc f_container_reference_create_nested_body { r cr i type } {
        set c [ get $cr ]
        __container_create_nested $r $c $i $type
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc f_container_reference_create_nested { r cr i type } {
        upvar 1 $r v

        # Create reference
        data_new tmp_r
        create_integer $tmp_r
        set v $tmp_r

        set rule_id [ rule_new ]
        rule $rule_id fcrcn "" "$cr $i" \
           "tp: f_container_reference_create_nested_body $tmp_r $cr $i $type"
    }

    proc f_container_reference_create_nested_body { r cr i type } {
        set c [ get $cr ]
        set s [ get $i ]
        container_create_nested $r $c $s $type
    }

    variable container_branches

    # c: container
    # r: rule id
    proc container_branch_post { r c } {

        variable container_branches

        puts "container_branch_post: rule: $r container: $c"
        dict lappend container_branches $r $c
        adlb::slot_create $c
    }

    # r: rule id
    proc container_branch_complete { r } {

        variable container_branches

        puts "container_branch_complete: $r"
        set cL [ dict get $container_branches $r ]
        foreach c $cL {
            puts "container: $c"
            adlb::slot_drop $c
        }
        set container_branches [ dict remove $container_branches ]
    }

    variable subs
    variable map_rule_stack
    variable map_stack_container

    proc scope_enter { stack } {
        dict lappend subs $stack
    }

    proc scope_add { stack rule_id } {
        dict incr subs $stack
        dict set rule_map $rule_id $stack
    }

    proc scope_container { stack c } {


    }

    proc scope_complete { rule_id } {

        puts "scope_complete: $rule_id"


        # Do we need this?
        # set stack [ dict get rule_map $rule_id ]
    }

    proc scope_decr { stack sub } {
        dict incr subs $stack -1
        set count [ dict get subs $stack ]
        if { count == 0 } {
            scope_exit $stack
        }
    }

    proc scope_exit { stack } {
        variable complete_rank

    }

    proc f_close_container { c r } {
        puts "f_close_container: $c $r"
    }

    # concatenates two strings
    proc strcat { parent c inputs } {
        set a [ lindex $inputs 0 ]
        set b [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "strcat-$a-$b" "$a $b" $c \
            "tl: strcat_body $parent $c $a $b"
    }

    proc strcat_body { parent c a b } {
        set a_value [ string_get $a ]
        set b_value [ string_get $b ]
        set c_value "${a_value}${b_value}"
        log "strcat: strcat($a_value, $b_value) => $c_value"
        string_set $c $c_value
    }
}
