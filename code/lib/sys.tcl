
# Turbine SYS.TCL

# Turbine system interface functionality

namespace eval turbine {

    # Name of Turbine Tcl program
    variable turbine_program
    # Count of unflagged arguments
    variable turbine_argc
    # Map from key to value for flagged arguments like:
    # -key or --key or -key= or -key=value or --key=value
    variable turbine_argv
    # Simple string containing all arguments (unused)
    variable turbine_args

    # Called by turbine::init to setup Turbine's argv
    proc argv_init { } {

        global argc
        global argv
        variable turbine_program
        variable turbine_argc
        variable turbine_argv
        variable turbine_args
        variable mode

        if { ! [ string equal $mode ENGINE ] } return

        set turbine_program [ info script ]
        set turbine_argc 0
        set turbine_argv [ dict create ]
        set turbine_args $::argv

        # Set Tcl program name at argv(0)
        dict set turbine_argv 0 $turbine_program

        set L [ argv_helper $::argv ]
        for { set i 0 } { $i < $argc } { incr i } {
            set arg [ lindex $L $i ]
            # String replacement may cause early break:
            if [ string equal $arg "" ] break
            set tokens [ ::split $arg = ]
            set token [ lindex $tokens 0 ]
            if { [ string index $token 0 ] == "-" } {
                set key [ string range $token 1 end ]
                if { [ string index $key 0 ] == "-" } {
                    set key [ string range $key 1 end ]
                }
                set value [ lindex $tokens 1 ]
            } else {
                incr turbine_argc
                set key $turbine_argc
                set value $token
            }

            dict set turbine_argv $key $value
        }
    }

    # Replace shell quoted spaced arguments with Tcl list
    proc argv_helper { s } {
        set result [ list ]
        # Index into string s
        set i 0
        # Index into list s
        set p 0
        set length [ llength $s ]
        # Find arg of form --key=\"val1 val2\"
        # and replace with \{--key=val1 val2\}
        for { } { $p < $length } { incr p } {
            set t [ lindex $s $p ]
            set j1 [ string first "-" $t $i ]
            if { $j1 == -1 } {
                lappend result $t
                continue
            }
            set j2 [ string first "\"" $t $i ]
            if { $j2 == -1 } {
                lappend result $t
                continue
            }
            set t [ string replace $t $j2 $j2 "" ]
            set matched 0
            for { incr p } { $p < $length } { incr p } {
                set w [ lindex $s $p ]
                set t "$t $w"
                set j4 [ string first "\"" $t ]
                if { $j4 == -1 } continue
                set matched 1
                set j5 [ expr $j4 - 1 ]
                set t [ string replace $t $j4 $j4 "" ]
                set i $j5
                break
            }
            if { ! $matched } { error "argv: unmatched quotes!" }
            lappend result $t
        }
        return $result
    }

    proc argc_get { stack result inputs } {
        # ignore inputs
        variable turbine_argc
        store_integer $result $turbine_argc
    }

    proc argc_get_impl { } {
        variable turbine_argc
        return $turbine_argc
    }

    proc args_get { stack result inputs } {
        # ignore inputs
        variable turbine_args
        store_string $result $turbine_args
    }

    proc args_get_local { } {
        variable turbine_args
        return $turbine_args
    }

    proc argv_contains { stack result key } {
        rule "argv_contains-$key" $key $turbine::LOCAL \
            "argv_contains_body $result $key"
    }

    proc argv_contains_body { result key } {
        set t [ retrieve_string $key ]
        store_integer $result [ argv_contains_impl $t ]
    }

    proc argv_contains_impl { key } {
        variable turbine_argv
        if { [ catch { set val [ dict get $turbine_argv $key ] } ] } {
            return 0
        } else {
            return 1
        }
    }

    # usage: argv_get <result> <key> <optional:base>
    # If key is not found, the base (default value) is used
    # "default" is a Tcl keyword so we call it "base"
    proc argv_get { args } {

        set stack  [ lindex $args 0 ]
        set result [ lindex $args 1 ]
        set key    [ lindex $args 2 ]
        set base ""
        if { [ llength $args ] == 4 }  {
            set base [ lindex $args 3 ]
        }

        rule "argv_get-$key" $key $turbine::LOCAL \
            "argv_get_body $result $key $base"
    }

    # usage: argv_get <result> <key> <optional:base>
    proc argv_get_body { args } {


        set c [ llength $args ]
        if { $c != 2 && $c != 3 } {
            error "argv_get_body: args: $c"
        }

        set result [ lindex $args 0 ]
        set key    [ lindex $args 1 ]

        set key_val [ retrieve $key ]
        if { $c == 2 } {
            set result_val [ argv_get_impl $key_val ]
        } elseif { $c == 3 } {
            set base [ lindex $args 2 ]
            set base_val [ retrieve_string $base ]
            set result_val [ argv_get_impl $key_val $base_val ]
        }

        store_string $result $result_val
    }

    proc argv_get_impl { key args } {
        variable turbine_argv
        variable error_code
        set c [ llength $args ]
        if { $c == 0 } {
            set base_defined 0
        } elseif { $c == 1 } {
            set base_defined 1
            set base [ lindex $args 0 ]
        } else {
           error "argv_get_body: args: $c"
        }
        if { [ catch { set val [ dict get $turbine_argv $key ] } ] } {
            if { ! $base_defined } {
                return -code $error_code "Could not find argv($key)"
            }
            return $base
        }
        return $val
    }

    proc argv_accept { args } {
        set stack [ lindex 0 ]
        set L [ lindex $args 2 ]
        rule argv_accept "$L" $turbine::LOCAL "argv_accept_body $L"
    }

    proc argv_accept_body { args } {

        variable turbine_argv

        if { [ adlb::rank ] != 0 } {
            return
        }

        set accepted [ list ]
        foreach td $args {
            lappend accepted [ retrieve_string $td ]
        }
        argv_accept_impl $accepted
    }
    proc argv_accept_impl { accepted } {
        variable turbine_argv

        dict for { key value } $turbine_argv {
            if [ string is integer $key ] continue
            if { [ lsearch $accepted $key ] == -1 } {
                error "argv_accept: not accepted: $key"
            }
        }
    }
    
    proc getenv { stack outputs inputs } {
        rule getenv-$inputs $inputs $turbine::LOCAL \
            "turbine::getenv_body $outputs $inputs"
    }
    proc getenv_body { result key } {
        global env
        set key_value [ retrieve_string $key ]
        store_string $result [ getenv_impl $key_value ]
    }

    proc getenv_impl { key } {
        if [ info exists env($key) ] {
            set result_value $env($key)
        } else {
            set result_value ""
        }
    }

    proc sleep { stack outputs inputs } {
        rule "sleep-$outputs-$inputs" $inputs $turbine::WORK \
            "turbine::sleep_body $outputs $inputs"
    }
    proc sleep_body { output secs } {
        set secs_val [ retrieve_float $secs ]
        after [ expr round($secs_val * 1000) ]
        store_void $output
    }
}
