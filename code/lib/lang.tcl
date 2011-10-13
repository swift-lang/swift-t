
# Turbine language

namespace eval turbine {

    namespace export program

    proc program { body } {

        ::eval $body

        global env
        init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
        start rules
        finalize
    }

    proc main { body } {
        proc rules { } $body
    }

    proc function { name params body } {
        proc $name $params $body
    }

    proc declare { type name args } {
        set td [ data_new ]
        if { [ string equal $type file ] } {
            assert [ string length $args ] \
                "declare file requires filename!"
            file_init $td $args
        } else {
            # string or integer
            eval ${type}_init $td
            if { [ string length $args ] } {
                eval ${type}_set $td $args
            }
        }
        uplevel 1 "set $name $td"
    }

    # "ready" and "creates" are ignored keywords
    proc when { ready inputs action creates outputs } {
        set r [ rule_new ]
        set input_tds [ list ]
        foreach t $inputs {
            upvar $t td
            lappend input_tds $td
        }
        set output_tds [ list ]
        foreach t $outputs {
            upvar $t td
            lappend output_tds $td
        }
        set first 1
        set action_list [ list ]
        puts "action: $action"
        foreach t $action {
            puts "t: $t"
            puts "action_list: $action_list"
            if { $first } {
                # First token is command name
                lappend action_list $t
                set first 0
                continue
            }
            upvar $t td
            lappend action_list $td
        }

        rule $r $r $input_tds $output_tds "tf: $action_list"
    }

    proc shell { command args } {
        puts "shell: $command $args"
        exec $command $args
    }

    proc @ { f } {
        upvar $f td
        puts "@: $td"
        return [ filename $td ]
    }
}
