
# Test trace and basic numerical functionality

# SwiftScript
# x = (3+5)*(3+5);
# trace(x);

package require turbine 0.0.1

proc rules { } {

    namespace import turbine::data_new
    namespace import turbine::float_*
    namespace import turbine::arithmetic

    data_new t1
    data_new t2
    data_new x

    float_init $t1
    #float_init $t2
    #float_init $x

    #float_set $t1 3
    #float_set $t2 5

    # Use 0 as stack frame
    # turbine::plus 0 [ $t1 $t2 ] [ $x ]
    turbine::trace 0 "" $x
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
