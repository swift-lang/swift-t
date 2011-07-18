
# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 0.1
turbine::init

turbine::c::string 1
turbine::c::string 2
# turbine::c::string 3

turbine::c::string_set 1 "hi"
turbine::c::string_set 2 "bye"

set v1 [ turbine::c::string_get 1 ]
set v2 [ turbine::c::string_get 2 ]

puts -nonewline "result: "
turbine::trace 1 2

turbine::engine
turbine::finalize

puts OK
