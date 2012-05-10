
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
    namespace export argv_get enumerate literal shell

    # Memory functions (will be in turbine::f namespace)
    namespace export f_dereference

    # System functions
    namespace export stack_lookup

    # These are Swift-2 functions
    namespace export set1

    # Bring in Turbine extension features
    namespace import c::new c::rule c::typeof
    namespace import c::insert c::log

    proc call_composite { stack f outputs inputs blockon } {

        set rule_id [ turbine::c::rule $f "$blockon"   \
                          $turbine::CONTROL            \
                          "$f $stack $outputs $inputs" ]
        return $rule_id
    }

    # User function
    # This name conflicts with a TCL built-in - it cannot be exported
    # TODO: Replace this with tracef()
    proc trace { args } {

        # parent stack and output arguments not read
        set tds [ lindex $args 2 ]
        if { ! [ string length $tds ] } {
            error "trace: received no arguments!"
        }
        rule "trace" $tds $turbine::LOCAL \
            "turbine::trace_body $tds"
    }


    proc trace_body { args } {
        set valuelist [ list ]
        foreach v $args {
            set value [ retrieve $v ]
            lappend valuelist $value
        }
        trace_impl2 $valuelist
    }
    proc trace_impl { args } {
        # variadic version
        trace_impl2 $args
    }

    proc trace_impl2 { arglist } {
        set n [ llength $arglist ]
        puts -nonewline "trace: "
        set first 1
        foreach value $arglist {
            if { $first } {
              set first 0
            } else {
              puts -nonewline ","
            }
            puts -nonewline $value
        }
        puts ""
    }

    # For tests/debugging
    proc sleep_trace { stack outputs inputs } {
        # parent stack and output arguments not read
        if { ! [ string length $inputs ] } {
            error "trace: received no arguments!"
        }
        set secs [ lindex $inputs 0 ]
        set args [ lreplace $inputs 0 0]
        rule "sleep_trace" $inputs $turbine::WORK \
            "turbine::sleep_trace_body $secs $args"
    }
    proc sleep_trace_body { secs inputs } {
        set secs_val [ retrieve_float $secs ]
        after [ expr round($secs_val * 1000) ]
        puts "AFTER"
        trace_body $inputs
    }

    # User function
    proc range { stack result inputs } {
        # Assume that there was a container slot opened
        # that can be owned by range (this works with stc's calling
        #   conventions which don't close assigned arrays)
        set start [ lindex $inputs 0 ]
        set end [ lindex $inputs 1 ]
        rule "range-$result" "$start $end" $turbine::CONTROL \
            "range_body $result $start $end"
    }

    proc range_body { result start end } {

        set start_value [ retrieve_integer $start ]
        set end_value   [ retrieve_integer $end ]

        range_work $result $start_value $end_value 1
    }

    proc rangestep { stack result inputs } {
        # Assume that there was a container slot opened
        # that can be owned by range
        set start [ lindex $inputs 0 ]
        set end [ lindex $inputs 1 ]
        set step [ lindex $inputs 2 ]
        rule "rangestep-$result" [ list $start $end $step ] \
            $turbine::CONTROL \
            "rangestep_body $result $start $end $step"
    }

    proc rangestep_body { result start end step } {

        set start_value [ retrieve_integer $start ]
        set end_value   [ retrieve_integer $end ]
        set step_value   [ retrieve_integer $step ]

        range_work $result $start_value $end_value $step_value
    }

    proc range_work { result start end step } {
        set k 0
        for { set i $start } { $i <= $end } { incr i $step } {
            allocate td integer
            store_integer $td $i
            container_insert $result $k $td
            incr k
        }
        adlb::slot_drop $result
    }

    # User function
    # Construct a distributed container of sequential integers
    proc drange { result start end parts } {

        rule "drange-$result" "$start $end" $turbine::CONTROL \
            "drange_body $result $start $end $parts"
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
                "command priority: $turbine::priority range_work $c $s $e 1" \
                $turbine::priority
        }
        close_datum $result
    }

    # User function
    # Loop over a distributed container
    proc dloop { loop_body stack container } {

        c::log "log_dloop:"
        rule "dloop-$container" $container $turbine::CONTROL \
            "dloop_body $loop_body $stack $container"
    }

    proc dloop_body { loop_body stack container } {

        set keys [ container_list $container ]

        global WORK_TYPE
        foreach key $keys {
            c::log "log_dloop_body"
            set c [ container_get $container $key ]
            release "loop_body $loop_body $stack $c"
        }
    }

    proc readdata { result filename } {

        rule "read_data-$filename" $filename $turbine::CONTROL \
            "readdata_body $result $filename"
    }

    proc readdata_body { result filename } {

        set name_value [ get $filename ]
        if { [ catch { set fd [ open $name_value r ] } e ] } {
            error "Could not open file: '$name_value'"
        }

        set i 0
        while { [ gets $fd line ] >= 0 } {
            allocate s string
            store_string $s $line
            container_insert $result $i $s
            incr i
        }
        close_datum $result
    }

    # User function
    proc loop { stmts stack container } {
        rule "loop-$container" $container $turbine::CONTROL \
            "loop_body $stmts $stack $container"
    }

    proc loop_body { stmts stack container } {
        set type [ container_typeof $container ]
        set L    [ container_list $container ]
        c::log "loop_body start"
        foreach subscript $L {
            set td_key [ literal $type $subscript ]
            # Call user body with subscript as TD
            # TODO: shouldn't this be an adlb::put ? -Justin
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
            set result [ allocate $name $type ]
            upvar 1 $name n
            set n $result
        } else {
            error "turbine::literal requires 2 or 3 args!"
        }

        store_${type} $result $value

        return $result
    }

    # User function
    proc toint { stack result input } {
        rule "toint-$input" $input $turbine::LOCAL \
            "toint_body $input $result"
    }

    proc toint_body { input result } {
      set t [ retrieve $input ]
      store_integer $result [ check_str_int $t ]
    }

    proc check_str_int { input } {
      if { ! [ string is integer $input ] } {
        error "could not convert string '${input}' to integer"
      }
      return $input
    }

    proc fromint { stack result input } {
      rule "fromint-$input-$result" $input $turbine::LOCAL \
            "fromint_body $input $result"
    }

    proc fromint_body { input result } {
        set t [ retrieve_integer $input ]
        # Tcl performs the conversion naturally
        store_string $result $t
    }

    proc tofloat { stack result input } {
        rule "tofloat-$input" $input $turbine::LOCAL \
            "tofloat_body $input $result"
    }

    proc tofloat_body { input result } {
        set t [ get $input ]
        #TODO: would be better if the accepted double types
        #     matched Swift float literals
        store_float $result [ check_str_float $t ]
    }

    proc check_str_float { input } {
      if { ! [ string is double $input ] } {
        error "could not convert string '${input}' to float"
      }
      return $input
    }

    proc fromfloat { stack result input } {
        rule "fromfloat-$input-$result" $input $turbine::LOCAL \
            "fromfloat_body $input $result"
    }

    proc fromfloat_body { input result } {
        set t [ get $input ]
        # Tcl performs the conversion naturally
        store_string $result $t
    }

    proc blob_from_string { stack result input } {
        rule "bfs-$input-$result" $input $turbine::LOCAL \
            "blob_from_string_body $input $result"
    }

    proc blob_from_string_body { input result } {
        set t [ retrieve $input ]
        store_blob_string $result $t
    }

    proc string_from_blob { stack result input } {
        rule "sfb-$input-$result" $input $turbine::LOCAL \
            "string_from_blob_body $input $result"
    }

    proc string_from_blob_body { input result } {
        set s [ retrieve_blob_string $input ]
        store_string $result $s
    }

    # Container must be indexed from 0,N-1
    proc blob_from_floats { stack result input } {
        rule "bff-$input-$result" $input $turbine::LOCAL \
            "blob_from_floats_body $input $result"
    }

    proc blob_from_floats_body { container result } {

        set type [ container_typeof $container ]
        set N    [ adlb::container_size $container ]
        c::log "bff_body start"
        set A [ list ]
        for { set i 0 } { $i < $N } { incr i } {
            set td [ container_lookup $container $i ]
            set v  [ retrieve_float $td ]
            lappend A $v
        }

        adlb::store_blob_floats $result $A
        turbine::close_datum $result
    }

    # Good for performance testing
    # c = 1;
    # and sleeps
    proc set0 { parent c } {

        rule "set0-$" "" $turbine::WORK "set0_body $parent $c"
    }
    proc set0_body { parent c } {
        log "set0"

        variable stats
        dict incr stats set0

        # Emulate some computation time
        # after 1000
        store_integer $c 0
    }

    # Good for performance testing
    # c = 1;
    # and sleeps
    proc set1 { parent c } {

        rule "set1-$" "" $turbine::WORK "set1_body $parent $c"
    }
    proc set1_body { parent c } {
        log "set1"

        variable stats
        dict incr stats set1

        # Emulate some computation time
        # after 1000
        store_integer $c 1
    }

    # Execute shell command
    proc shell { args } {
        puts "turbine::shell $args"
        set command [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]
        rule "shell-$command" $inputs $turbine::WORK \
            "shell_body $command \"$inputs\""
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

    # o = i.  Void has no value, so this just makes sure that they close at
    # the same time
    proc copy_void { parent o i } {
        rule "copy-$o-$i" $i $turbine::LOCAL "copy_void_body $o $i"
    }
    proc copy_void_body { o i } {
        log "copy_void $i => $o"
        store_void $o
    }

    # create a void type (i.e. just set it)
    proc make_void { parent o i } {
        empty i
        store_void $o
    }
}
