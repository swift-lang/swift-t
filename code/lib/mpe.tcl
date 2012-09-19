
# MPE FEATURES

# Note that if MPE is not enabled, the Tcl MPE extensions are noops

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
        }
    }

    # Add an arbitrary string to the MPE log as "metadata"
    # The MPE-defined string length limit is 32
    proc metadata { stack result input } {
        turbine::rule "metadata-$input" $input $turbine::WORK \
            "turbine::metadata_body $input"
    }

    proc metadata_body { message } {
       metadata_impl [ turbine::retrieve_string $message ]
    }

    proc metadata_impl { msg } {
        variable event
        mpe_setup
        mpe::log $event "$msg"
    }
}
