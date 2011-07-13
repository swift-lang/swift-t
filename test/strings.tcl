
# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 0.1
turbine_init

turbine_string 1
turbine_string 2
# turbine_string 3

turbine_string_set 1 "hi"
turbine_string_set 2 "bye"

set v1 [ turbine_string_get 1 ]
set v2 [ turbine_string_get 2 ]
# set v3 [ expr $v1 + $v2 ]

# turbine_integer_set 3 $v3

puts -nonewline "result: "
turbine_trace 1 2

turbine_engine
turbine_finalize
puts OK
