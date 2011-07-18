
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.1
turbine::init

turbine::c::integer 1
turbine::c::integer 2
turbine::c::integer 3

turbine::c::integer_set 1 3
turbine::c::integer_set 2 5

set v1 [ turbine::c::integer_get 1 ]
set v2 [ turbine::c::integer_get 2 ]

turbine::arithmetic 3 "(_+_)*(_+_)" 1 2 1 2

turbine::trace 3

turbine::engine
turbine::finalize
puts OK
