
# Benchmarking utility functions

namespace eval bench {

    package require turbine 0.0.2

    package provide bench 0.0.1

    # namespace import turbine::c::rule turbine::set_integer

    # Set by mpe_setup
    variable mpe_ready

    # MPE event IDs
    variable event

    proc mpe_setup { } {

        variable event
        set event_names [ list set1 set1rA set1rB ]

        if { ! [ info exists mpe_ready ] } {

            foreach e $event_names {
                set L [ mpe::create $e ]
                set event(start_$e) [ lindex $L 0 ]
                set event(stop_$e)  [ lindex $L 1 ]

                set mpe_ready 1
            }
        }
    }

    # usage: set1_float no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1_float { stack result delay } {
        turbine::rule "set1-$result" $delay $turbine::WORK \
            "bench::set1_float_body $result $delay"
    }

    proc set1_float_body { result delay } {

        variable event

        mpe_setup
        set delay_value [ get_float $delay ]
        mpe::log $event(start_set1)
        after [ expr round($delay_value) ]
        mpe::log $event(stop_set1)
        set_integer $result 1
    }

    # usage: set1_integer no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1_integer { stack result delay } {
        turbine::rule "set1-$result" $delay $turbine::WORK \
            "bench::set1_integer_body $result $delay"
    }

    proc set1_integer_body { result delay } {
        set delay_value [ get_integer $delay ]
        after $delay_value
        set_integer $result 1
    }

    # usage: set1_integer no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1rA_integer { stack result delay } {
        turbine::rule "set1r-$result" $delay $turbine::WORK \
            "bench::set1rA_integer_body $result $delay"
    }

    proc set1rA_integer_body { result delay } {

        variable event
        mpe_setup
        set delay_value [ get_integer $delay ]
        # randomized delay value:
        mpe::log $event(start_set1rA)
        set rdv [ expr rand() * $delay_value ]
        after [ expr round($rdv) ]
        mpe::log $event(stop_set1rA)
        set_integer $result 1
    }

    # usage: set1_integer no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1rB_integer { stack result delay } {
        turbine::rule "set1r-$result" $delay $turbine::WORK \
            "bench::set1rB_integer_body $result $delay"
    }

    proc set1rB_integer_body { result delay } {

        variable event
        mpe_setup
        set delay_value [ get_integer $delay ]
        # randomized delay value:
        mpe::log $event(start_set1rB)
        set rdv [ expr rand() * $delay_value ]
        after [ expr round($rdv) ]
        mpe::log $event(stop_set1rB)
        set_integer $result 1
    }
}
