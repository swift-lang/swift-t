
# MPE FEATURES

namespace eval turbine {

    # Set by mpe_setup
    variable mpe_ready

    # MPE event IDs
    variable event

    proc mpe_setup { } {

        variable mpe_ready
        variable event
        set event_names [ list metadata ]
        # debug set1 set1rA set1rB sum

        if { ! [ info exists mpe_ready ] } {

            foreach e $event_names {
                set L [ mpe::create $e ]
                set event(start_$e) [ lindex $L 0 ]
                set event(stop_$e)  [ lindex $L 1 ]
            }
            set mpe_ready 1
        }
    }

    # Add an arbitrary string to the MPE log as "metadata"
    # The string length limit is 32
    proc metadata { stack result input } {
        turbine::rule "metadata-$input" $input $turbine::WORK \
            "turbine::metadata_body $input"
    }

    proc metadata_body { message } {

        variable event
        mpe_setup

        set message_value [ turbine::retrieve_string $message ]
        mpe::log $event(start_metadata) "$message_value"
    }
}
