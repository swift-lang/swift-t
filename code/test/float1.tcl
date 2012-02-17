
# Test trace and basic numerical functionality

# SwiftScript
# x = (3+5)*(3+5);
# trace(x);

package require turbine 0.0.1

proc rules { } {

    namespace import turbine::data_new
    namespace import turbine::float_*
    namespace import turbine::arithmetic

    set t1 [ data_new ]
    set t2 [ data_new ]
    set x  [ data_new ]

    float_init $t1
    float_init $t2
    float_init $x

    float_set $t1 3
    float_set $t2 5

    # Use 0 as stack frame
    turbine::plus no_stack [ $t1 $t2 ] [ $x ]
    turbine::trace 0 "" $x
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize

puts OK
