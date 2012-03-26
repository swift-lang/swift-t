
# Turbine UNISTD.TCL

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
        literal argv_td string $turbine_program
        dict set turbine_argv 0 $argv_td

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

            literal argv_td string $value
            dict set turbine_argv $key $argv_td
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
        set_integer $result $turbine_argc
    }

    proc args_get { stack result inputs } {
        # ignore inputs
        variable turbine_args
        set_string $result $turbine_args
    }

    proc argv_contains { stack result key } {
        rule "argv_contains-$key" $key $turbine::LOCAL \
            "argv_contains_body $result $key"
    }

    proc argv_contains_body { result key } {

        variable turbine_argv
        set t [ get $key ]
        if { [ catch { set td [ dict get $turbine_argv $t ] } ] } {
            set_integer $result 0
        } else {
            set_integer $result 1
        }
    }

    # User function
    # usage: argv_get <result> <optional:default> <key>
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

    # usage: argv_get <result> <key> <optional:default>
    # "default" is a Tcl keyword so we call it "base"
    proc argv_get_body { args } {

        variable turbine_argv

        set c [ llength $args ]
        if { $c != 2 && $c != 3 } {
            error "argv_get_body: args: $c"
        }

        set result [ lindex $args 0 ]
        set key    [ lindex $args 1 ]
        if { $c == 2 } {
            set base 0
        } elseif { $c == 3 } {
            set base [ lindex $args 2 ]
        }

        set t [ get $key ]
        if { [ catch { set td [ dict get $turbine_argv $t ] } ] } {
            if { ! $base } {
                error "Could not find argv($t)"
            }
            set_string $result [ get_string $base ]
            return
        }
        set_string $result [ get_string $td ]
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
            lappend accepted [ get_string $td ]
        }
        dict for { key value } $turbine_argv {
            if [ string is integer $key ] continue
            if { [ lsearch $accepted $key ] == -1 } {
                error "argv_accept: not accepted: $key"
            }
        }
    }
}
