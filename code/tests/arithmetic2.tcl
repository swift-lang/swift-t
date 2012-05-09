
# Test trace and basic numerical functionality

# SwiftScript
# x = (3+5)*(3+5);
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::allocate t3 integer
    turbine::allocate t5 integer
    turbine::allocate x integer

    turbine::set_integer $t3 3
    turbine::set_integer $t5 5

    # Use 0 as stack frame
    turbine::arithmetic 0 $x [ list "(_+_)*(_+_)" $t3 $t5 $t3 $t5 ]
    turbine::trace 0 "" $x
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
