
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.1
turbine_init

turbine_integer 1
turbine_integer 2
turbine_string  3
turbine_container 4 key integer

turbine_integer_set 1 1
turbine_integer_set 2 4

turbine_range 4 1 2
turbine_enumerate 3 4
turbine_trace 3

# puts -nonewline "result: "
# turbine_trace 3

turbine_engine
turbine_finalize
puts OK
