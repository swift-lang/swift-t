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

# Turbine builtin functions

# All builtins will have signature:
#   f <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs
# even if some of the arguments are not used
# The uniformity allows the STC code generator to simply write all
# calls to builtins the same way

namespace eval turbine {

    # User functions
    namespace export enumerate literal shell call_composite

    # Debugging/performance-testing functions
    namespace export set1

    # Bring in Turbine extension features
    namespace import c::new c::typeof
    namespace import c::insert c::log

    # Use C version of toint_impl
    namespace import c::toint_impl
    namespace import c::parse_int_impl

    # This name conflicts with a Tcl built-in - it cannot be exported
    proc trace { signal inputs } {
        rule $inputs "trace_body \"$signal\" $inputs" \
            name "trace"
    }

    proc trace_body { signal args } {
        set valuelist [ list ]
        foreach v $args {
            set value [ retrieve_decr $v ]
            lappend valuelist $value
        }
        trace_impl2 $valuelist
        if { $signal != "" } {
            store_void $signal
        }
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

    # # For tests/debugging
    proc sleep_trace { signal inputs } {
      # parent stack and output arguments not read
      if { ! [ llength $inputs ] } {
        error "trace: received no arguments!"
      }
      rule $inputs "sleep_trace_body $signal $inputs" type $::turbine::WORK
    }
    proc sleep_trace_body { signal secs args } {
      set secs_val [ retrieve_decr_float $secs ]
      after [ expr {round($secs_val * 1000)} ]
      puts "AFTER"
      trace_body $signal $args
    }

    # User function
    proc range { result inputs } {
        # Assume that there was a container slot opened
        # that can be owned by range (this works with stc's calling
        #   conventions which don't close assigned arrays)
        set start [ lindex $inputs 0 ]
        set end [ lindex $inputs 1 ]
        rule [ list $start $end ] "range_body $result $start $end" \
              type $turbine::CONTROL name "range-$result"
    }

    proc range_body { result start end } {

        set start_value [ retrieve_decr_integer $start ]
        set end_value   [ retrieve_decr_integer $end ]

        range_work $result $start_value $end_value 1
    }

    proc range_float { result inputs } {
        set start [ lindex $inputs 0 ]
        set end [ lindex $inputs 1 ]
        rule [ list $start $end ] "range_float_body $result $start $end" \
              type $turbine::CONTROL name "range_float-$result"
    }

    proc range_float_body { result start end } {

        set start_value [ retrieve_decr_float $start ]
        set end_value   [ retrieve_decr_float $end ]

        range_float_work $result $start_value $end_value 1.0
    }

    proc range_step { result inputs } {
        # Assume that there was a container slot opened
        # that can be owned by range
        set start [ lindex $inputs 0 ]
        set end [ lindex $inputs 1 ]
        set step [ lindex $inputs 2 ]
        rule [ list $start $end $step ] \
            "range_step_body $result $start $end $step" type $turbine::CONTROL
    }

    proc range_step_body { result start end step } {

        set start_value [ retrieve_decr_integer $start ]
        set end_value   [ retrieve_decr_integer $end ]
        set step_value   [ retrieve_decr_integer $step ]

        range_work $result $start_value $end_value $step_value
    }

    proc range_float_step { result inputs } {
        # Assume that there was a container slot opened
        # that can be owned by range
        set start [ lindex $inputs 0 ]
        set end [ lindex $inputs 1 ]
        set step [ lindex $inputs 2 ]
        rule [ list $start $end $step ] \
            "range_float_step_body $result $start $end $step" type $turbine::CONTROL
    }

    proc range_float_step_body { result start end step } {

        set start_value [ retrieve_decr_float $start ]
        set end_value   [ retrieve_decr_float $end ]
        set step_value   [ retrieve_decr_float $step ]

        range_float_work $result $start_value $end_value $step_value
    }

    proc range_work { result start end step } {
        set kv_dict [ build_range_dict $start $end $step ]
        array_kv_build $result $kv_dict 1 integer integer
    }

    proc range_float_work { result start end step } {
        set kv_dict [ build_range_float_dict $start $end $step ]
        array_kv_build $result $kv_dict 1 integer float
    }

    proc build_range_dict { start end step } {
        set kv_dict [ dict create ]
        set k 0
        for { set v $start } { $v <= $end } { incr v $step } {
            dict append kv_dict $k $v
            incr k
        }
        return $kv_dict
    }

    proc build_range_float_dict { start end step } {
        set kv_dict [ dict create ]
        set k 0

        while { 1 } {
          # Compute in this way to avoid cumulative rounding errors
          set v [ expr {$start + $k * $step} ]

          if { $v <= $end } {
            dict append kv_dict $k $v
          } else {
            break
          }

          incr k
        }

        return $kv_dict
    }

    # User function
    # Construct a distributed container of sequential integers
    proc drange { result start end parts } {
        rule "$start $end" "drange_body $result $start $end $parts" \
            type $turbine::CONTROL name "drange-$result"
    }

    proc drange_body { result start end parts } {

        set start_value [ retrieve_decr $start ]
        set end_value   [ retrieve_decr $end ]
        set parts_value [ retrieve_decr $parts ]
        set size        [ expr {$end_value - $start_value + 1} ]
        set step        [ expr {$size / $parts_value} ]

        global WORK_TYPE
        for { set i 0 } { $i < $parts_value } { incr i } {
            # top-level container
            allocate_container c integer ref
            container_insert $result $i $c ref
            # start
            set s [ expr {$i *  $step} ]
            # end
            set e [ expr {$s + $step - 1} ]

            set prio [ get_priority ]
            adlb::put $::adlb::RANK_ANY $WORK_TYPE(WORK) \
                "priority_command $prio range_work $c $s $e 1" \
                $prio 1
        }
        # close container
        write_refcount_decr $result
    }

    # User function
    # Loop over a distributed container
    proc dloop { loop_body stack container } {
        c::log "log_dloop:"
        rule $container "dloop_body $loop_body $stack $container" \
                         name $turbine::CONTROL
    }

    proc dloop_body { loop_body stack container } {

        set keys [ container_list $container ]

        global WORK_TYPE
        foreach key $keys {
            c::log "log_dloop_body"
            set c [ container_lookup $container $key ]
            release "loop_body $loop_body $stack $c"
        }
    }

    proc readdata { result filename } {
        rule $filename "readdata_body $result $filename" \
              type $turbine::CONTROL
    }

    proc readdata_body { result filename } {

        set name_value [ retrieve_decr $filename ]
        if { [ catch { set fd [ open $name_value r ] } e ] } {
            error "Could not open file: '$name_value'"
        }

        set i 0
        while { [ gets $fd line ] >= 0 } {
            allocate s string
            store_string $s $line
            container_insert $result $i $s ref
            incr i
        }
        write_refcount_decr $result
    }

    # User function
    proc loop { stmts stack container } {
        rule $container "loop_body $stmts $stack $container" \
              type $turbine::CONTROL name "loop-$container"
    }

    proc loop_body { stmts stack container } {
        lassign [ container_typeof $container ] key_type val_type
        set L    [ container_list $container ]
        c::log "loop_body start"
        foreach subscript $L {
            set td_key [ literal $key_type $subscript ]
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
            set result [ allocate_custom "" $type 1 1 0 1 ]
        } elseif { [ llength $args ] == 3 } {
            set name   [ lindex $args 0 ]
            set type   [ lindex $args 1 ]
            set value  [ lindex $args 2 ]
            set result [ allocate_custom $name $type 1 1 0 1 ]
            upvar 1 $name n
            set n $result
        } else {
            error "turbine::literal requires 2 or 3 args!"
        }

        store_${type} $result $value

        return $result
    }

    # User function
    proc toint { result input } {
        rule $input "toint_body $input $result" \
            name "toint-$input"
    }

    proc toint_body { input result } {
        set t [ retrieve_decr $input ]
        set i [ toint_impl $t ]

        store_integer $result $i
    }

    proc parse_int { result inputs } {
        rule $inputs [ list parse_int_body {*}$inputs $result ] \
            name "parse_int-$inputs"
    }

    proc parse_int_body { str base result } {
        set str_val [ retrieve_decr_string $str ]
        set base_val [ retrieve_decr_integer $base ]
        set i [ parse_int_impl $str_val $base_val ]

        store_integer $result $i
    }

    proc fromint { result input } {
        rule $input "fromint_body $result $input" \
            name "fromint-$input-$result"
    }

    proc fromint_body { result input } {
        set t [ retrieve_decr_integer $input ]
        # Tcl performs the conversion naturally
        store_string $result $t
    }

    proc tofloat { result input } {
        rule $input "tofloat_body $input $result" \
            name "tofloat-$input"
    }

    proc tofloat_body { input result } {
        set t [ retrieve_decr $input ]
        #TODO: would be better if the accepted double types
        #     matched Swift float literals
        store_float $result [ tofloat_impl $t ]
    }

    proc tofloat_impl { input } {
      if { ! [ string is double $input ] } {
        error "could not convert string '${input}' to float"
      }
      return $input
    }

    proc fromfloat { result input } {
        rule $input "fromfloat_body $input $result"
    }

    proc fromfloat_body { input result } {
        set t [ retrieve_decr $input ]
        # Tcl performs the conversion naturally
        store_string $result $t
    }

    proc boolToString { result input } {
        rule $input "boolToString_body $result $input"
    }

    proc boolToString_body { result input } {
        set v [ retrieve_decr_integer $input ]
        store_string $result [ boolToString_impl $v ]
    }

    proc boolToString_impl { input } {
      return [ expr {$input ? "true" : "false"} ]
    }

    # Good for performance testing
    # c = 0;
    # and sleeps
    proc set0 { c } {
        rule {} "set0_body $c" \
             name "set0-$" type $::turbine::WORK
    }
    proc set0_body { c } {
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
    proc set1 { c } {
        rule {} "set1_body $c" \
             name "set1-$" type $::turbine::WORK
    }
    proc set1_body { c } {
        log "set1"

        variable stats
        dict incr stats set1

        # Emulate some computation time
        # after 1000
        store_integer $c 1
    }

    # Execute shell command DEPRECATED
    proc shell { args } {
        puts "turbine::shell $args"
        set command [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]
        rule $inputs "shell_body $command \"$inputs\"" type $::turbine::WORK
    }

    proc shell_body { args } {
        set command [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]
        set values [ list ]
        foreach i $inputs {
            set value [ retrieve_decr $i ]
            lappend values $value
        }
        debug "executing: $command $values"
        exec $command $values
    }

    # o = i;
    proc copy_integer { o i } {
        rule $i "copy_integer_body $o $i" name "copy-$o-$i"
    }
    proc copy_integer_body { o i } {
        set i_value [ retrieve_decr_integer $i ]
        set o_value $i_value
        store_integer $o $o_value
    }

    # o = i;
    proc copy_float { o i } {
        rule $i "copy_float_body $o $i" name "copy-$o-$i"
    }
    proc copy_float_body { o i } {
        set i_value [ retrieve_decr_float $i ]
        set o_value $i_value
        store_float $o $o_value
    }

    # o = i.  Void has no value, so this just makes sure that
    #         they close sequentially
    proc copy_void { o i } {
        rule $i "copy_void_body $o $i" name "copy-$o-$i"
    }
    proc copy_void_body { o i } {
        store_void $o
        read_refcount_decr $i
    }

    # Copy string value
    proc copy_string { o i } {
        rule $i "copy_string_body $o $i" name "copystring-$o-$i"
    }
    proc copy_string_body { o i } {
        set i_value [ retrieve_decr_string $i ]
        store_string $o $i_value
    }

    # Copy blob value
    proc copy_blob { o i } {
        rule $i "copy_blob_body $o $i" name "copyblob-$o-$i"
    }
    proc copy_blob_body { o i } {
        set i_value [ retrieve_decr_blob $i ]
        store_blob $o $i_value
        free_blob $i
    }

    # create a void type (i.e. just set it)
    proc make_void { o i } {
        set inputs [ list ]
        foreach v $i {
            if { [ string first "file" $v ] != -1 } {
                set v [ get_file_td $v ]
            }
            lappend inputs $v
        }

        rule $inputs [ list make_void_body $o $inputs ] name make_void-$o
    }

    proc make_void_body { output inputs } {
        # inputs: may be empty list
        # Do this in reverse order for faster propagation
        # (Pretend to read inputs AFTER setting output!)
        store_void $output
        foreach v $inputs {
            read_refcount_decr $v
        }
    }

    proc zero { outputs inputs } {
        rule $inputs "zero_body $outputs $inputs" \
            name "zero-$outputs-$inputs"
    }
    proc zero_body { output input } {
        read_refcount_decr $input
        store_integer $output 0
    }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
