
# Turbine UNISTD.TCL

# Turbine system interface functionality

namespace eval turbine {

    variable turbine_argc
    variable turbine_argv

    # Called by turbine::init to setup Turbine's argv
    proc argv_init { } {

        global argc
        global argv
        variable turbine_argc
        variable turbine_argv
        variable mode

        if { ! [ string equal $mode ENGINE ] } return

        puts "argv_init $::argv"

        set turbine_argv [ dict create ]
        # Arguments that are not part of a -key=value pair
        set turbine_argc 0
        for { set i 0 } { $i < $argc } { incr i } {
            set arg [ lindex $argv $i ]
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
            # incr argc
        }
    }

    proc argc_get { stack result inputs } {
        # ignore inputs
        variable turbine_argc
        set_integer $result $turbine_argc
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
        puts "result $result"
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
        set L [ lreplace $args 0 0 ]
        rule argv_accept "$L" $turbine::LOCAL "argv_accept_body $L"
    }

    proc argv_accept_body { args } {

        variable turbine_argv

        puts "accept: $args"

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
