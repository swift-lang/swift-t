
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::create_integer 1
    turbine::create_integer 2
    turbine::create_string  3
    turbine::create_string  4
    turbine::create_float   5

    turbine::set_integer 1 2
    turbine::set_integer 2 2
    turbine::set_string  3 "(%i,%i,%s,%0.2f)"
    turbine::set_string  4 "howdy"
    turbine::set_float   5 3.1415

    turbine::printf no_stack 3 1 2 4 5
}

turbine::defaults
turbine::init $engines $servers

turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
