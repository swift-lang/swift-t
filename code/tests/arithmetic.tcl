
# Test trace and basic numerical functionality

# SwiftScript
# x = (3+5)*(3+5);
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::create_integer 1
    turbine::create_integer 2
    turbine::create_integer 3

    turbine::store_integer 1 3
    turbine::store_integer 2 5

    set v1 [ turbine::retrieve_integer 1 ]
    set v2 [ turbine::retrieve_integer 2 ]

    # Use 0 as stack frame
    turbine::arithmetic 0 3 [ list "(_+_)*(_+_)" 1 2 1 2 ]
    turbine::trace 0 "" 3
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
