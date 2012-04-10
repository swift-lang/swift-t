
# Benchmarking utility functions

namespace eval bench {

    package require turbine 0.0.2

    package provide bench 0.0.1

    # namespace import turbine::c::rule turbine::set_integer

    # Set by mpe_setup
    variable mpe_ready

    # MPE event IDs
    variable set1rA_event_start
    variable set1rA_event_stop
    variable set1rB_event_start
    variable set1rB_event_stop

    proc mpe_setup { } {

        variable mpe_ready
        variable set1rA_event_start
        variable set1rA_event_stop
        variable set1rB_event_start
        variable set1rB_event_stop

        puts CHECK
        if { ! [ info exists mpe_ready ] } {
            set L [ mpe::create "set1rA" ]
            set set1rA_event_start [ lindex $L 0 ]
            set set1rA_event_stop  [ lindex $L 1 ]

            set L [ mpe::create "set1rB" ]
            set set1rB_event_start [ lindex $L 0 ]
            set set1rB_event_stop  [ lindex $L 1 ]

            set mpe_ready 1
            puts OK
        }
    }

    # usage: set1_float no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1_float { stack result delay } {
        turbine::rule "set1-$result" $delay $turbine::WORK \
            "bench::set1_float_body $result $delay"
    }

    proc set1_float_body { result delay } {
        set delay_value [ get_float $delay ]
        after [ expr round($delay_value) ]
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

        mpe_setup
        variable set1rA_event_start
        variable set1rA_event_stop

        set delay_value [ get_integer $delay ]
        # randomized delay value:
        mpe::log $set1rA_event_start
        set rdv [ expr rand() * $delay_value ]
        after [ expr round($rdv) ]
        mpe::log $set1rA_event_stop
        set_integer $result 1
    }

    # usage: set1_integer no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1rB_integer { stack result delay } {
        turbine::rule "set1r-$result" $delay $turbine::WORK \
            "bench::set1rB_integer_body $result $delay"
    }

    proc set1rB_integer_body { result delay } {

        mpe_setup
        variable set1rB_event_start
        variable set1rB_event_stop

        set delay_value [ get_integer $delay ]
        # randomized delay value:
        mpe::log $set1rB_event_start
        set rdv [ expr rand() * $delay_value ]
        after [ expr round($rdv) ]
        mpe::log $set1rB_event_stop
        set_integer $result 1
    }
}
