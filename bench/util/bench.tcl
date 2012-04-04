
# Benchmarking utility functions

namespace eval bench {

    package require turbine 0.0.2

    package provide bench 0.0.1

    # namespace import turbine::c::rule turbine::set_integer

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
}
