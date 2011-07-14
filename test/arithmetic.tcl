
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.1
turbine_init

turbine_integer 1
turbine_integer 2
turbine_integer 3

turbine_integer_set 1 3
turbine_integer_set 2 5

set v1 [ turbine_integer_get 1 ]
set v2 [ turbine_integer_get 2 ]

turbine_arithmetic 3 "(_+_)*(_+_)" 1 2 1 2

turbine_trace 3

turbine_engine
turbine_finalize
puts OK
