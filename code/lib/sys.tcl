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
    # List of unflagged arguments by position
    variable turbine_argp
    # Simple string containing all arguments
    variable turbine_args

    # Called by turbine::init to setup Turbine's argv
    proc argv_init { } {

        global argc
        global argv
        variable turbine_program
        variable turbine_argc
        variable turbine_argv
        variable turbine_argp
        variable turbine_args
        variable mode

        if { ! [ string equal $mode ENGINE ] } return

        set turbine_program [ info script ]
        set turbine_argc 0
        set turbine_argv [ dict create ]
        set turbine_argp [ list ]
        set turbine_args $::argv

        # Set Tcl program name at argv(0)
        lappend turbine_argp $turbine_program

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
                dict set turbine_argv $key $value
            } else {
                lappend turbine_argp $arg
                incr turbine_argc
            }
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
                set j5 [ expr {$j4 - 1} ]
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
        rule "argv_contains-$key" $key $turbine::LOCAL $adlb::RANK_ANY \
            "argv_contains_body $result $key"
    }

    proc argv_contains_body { result key } {
        set t [ retrieve_decr_string $key ]
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

        rule "argv_get-$key" $key $turbine::LOCAL $adlb::RANK_ANY \
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

        set key_val [ retrieve_decr_string $key ]
        if { $c == 2 } {
            set result_val [ argv_get_impl $key_val ]
        } elseif { $c == 3 } {
            set base [ lindex $args 2 ]
            set base_val [ retrieve_decr_string $base ]
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

    # usage: argv_get <result> <index> <optional:base>
    # If index >= argc, the base (default value) is used
    # "default" is a Tcl keyword so we call it "base"
    proc argp_get { args } {
        set stack  [ lindex $args 0 ]
        set result [ lindex $args 1 ]
        set i    [ lindex $args 2 ]
        set base ""
        if { [ llength $args ] == 4 }  {
            set base [ lindex $args 3 ]
        }

        rule "argp_get-$i" $i $turbine::LOCAL $adlb::RANK_ANY \
            "argp_get_body $result $i $base"
    }

    # usage: argp_get <result> <index> <optional:base>
    proc argp_get_body { args } {
        set c [ llength $args ]
        if { $c != 2 && $c != 3 } {
            error "argp_get_body: args: $c"
        }

        set result [ lindex $args 0 ]
        set i    [ lindex $args 1 ]

        set i_val [ retrieve_decr_integer $i ]
        if { $c == 2 } {
            set result_val [ argp_get_impl $i_val ]
        } elseif { $c == 3 } {
            set base [ lindex $args 2 ]
            set base_val [ retrieve_decr_string $base ]
            set result_val [ argp_get_impl $i_val $base_val ]
        }

        store_string $result $result_val
    }

    proc argp_get_impl { i args } {
        variable turbine_argp
        variable turbine_argc
        variable error_code
        set c [ llength $args ]
        if { $c == 0 } {
            set base_defined 0
        } elseif { $c == 1 } {
            set base_defined 1
            set base [ lindex $args 0 ]
        } else {
            error "argp_get_body: args: $c"
        }
        if { $i < 0 } {
            error "argp_get_body: i < 0: $i"
        }
        if { $i > $turbine_argc } {
            if { ! $base_defined } {
                return -code $error_code "argp: index $i > argc $turbine_argc"
            }
            return $base
        } else {
            return [ lindex $turbine_argp $i ]
        }
    }

    proc argv_accept { args } {
        set stack [ lindex 0 ]
        set L [ lindex $args 2 ]
        rule argv_accept "$L" $turbine::LOCAL $adlb::RANK_ANY "argv_accept_body $L"
    }

    proc argv_accept_body { args } {

        variable turbine_argv

        if { [ adlb::rank ] != 0 } {
            return
        }

        set accepted [ list ]
        foreach td $args {
            lappend accepted [ retrieve_decr_string $td ]
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
        rule getenv-$inputs $inputs $turbine::LOCAL $adlb::RANK_ANY \
            "turbine::getenv_body $outputs $inputs"
    }
    proc getenv_body { result key } {
        set key_value [ retrieve_decr_string $key ]
        store_string $result [ getenv_impl $key_value ]
    }

    proc getenv_impl { key } {
        global env
        if [ info exists env($key) ] {
            set result_value $env($key)
        } else {
            set result_value ""
        }
    }

    # Sleep for given time in seconds.  Return void
    proc sleep { stack outputs inputs } {
        rule "sleep-$outputs-$inputs" $inputs $turbine::WORK \
            $adlb::RANK_ANY \
            "turbine::sleep_body $outputs $inputs"
    }
    proc sleep_body { output secs } {
        set secs_val [ retrieve_decr_float $secs ]
        after [ expr {round($secs_val * 1000)} ]
        store_void $output
    }

    # Busy wait for a number of seconds.  Up to microsecond precision
    proc spin { time_s } {
        set us { expr round($time_s * 1000000) }
        set start [ clock microseconds ]
        while { [ clock microseconds ] < $start + $us } {
            # Spin
        }
    }
}
