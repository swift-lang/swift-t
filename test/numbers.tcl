
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.1
turbine::init

turbine::c::integer_init 1
turbine::c::integer_init 2
turbine::c::integer_init 3

turbine::c::integer_set 1 2
turbine::c::integer_set 2 2

set v1 [ turbine::c::integer_get 1 ]
set v2 [ turbine::c::integer_get 2 ]
set v3 [ expr $v1 + $v2 ]

turbine::c::integer_set 3 $v3

turbine::trace 3

turbine::engine
turbine::finalize
puts OK
