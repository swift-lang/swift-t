
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
    namespace export f_dereference

    # System functions
    namespace export stack_lookup


    # These are Swift-2 functions
    namespace export set1

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
            set_string $v $value
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

        set t [ get_string $key ]
        if { [ catch { set v [ dict get $argv $t ] } ] } {
            set_string $result ""
            return
        }
        set_string $result $v
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
    #             set t [ get_string $src ]
    #             string_init $dest
    #             set_string $dest $t
    #         }
    #     }
    # }

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
}
