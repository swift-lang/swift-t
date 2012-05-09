
# Test trace and basic numerical functionality

# SwiftScript
# x = (3+5)*(3+5);
# trace(x);

package require turbine 0.0.1

proc rules { } {

    namespace import adlb::unique
    namespace import turbine::float_*
    namespace import turbine::arithmetic

    turbine::allocate t1 float
    turbine::allocate t2 float
    turbine::allocate x float

    turbine::set_float $t1 3
    turbine::set_float $t2 5

    # Use 0 as stack frame
    turbine::plus_float 0 [ list $x ] [ list $t1 $t2 ]
    turbine::trace 0 "" $x
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
