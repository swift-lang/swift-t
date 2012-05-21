
# MPE FEATURES

namespace eval turbine {

    # Set by mpe_setup
    variable mpe_ready

    # MPE event ID
    variable event

    proc mpe_setup { } {

        variable mpe_ready
        variable event

        if { ! [ info exists mpe_ready ] } {
            set event [ mpe::create_solo "metadata" ]
            set mpe_ready 1
            puts "metadata event ID: $event"
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
        mpe::log $event "$message_value"
    }
}
