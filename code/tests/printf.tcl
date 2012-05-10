
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::create_integer 11
    turbine::create_integer 12
    turbine::create_string  13
    turbine::create_string  14
    turbine::create_float   15

    turbine::store_integer 11 2
    turbine::store_integer 12 2
    turbine::store_string  13 "(%i,%i,%s,%0.2f)"
    turbine::store_string  14 "howdy"
    turbine::store_float   15 3.1415

    turbine::printf no_stack "" [ list 13 11 12 14 15 ]
}

turbine::defaults
turbine::init $engines $servers

turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
