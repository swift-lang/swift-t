
# Test trace and basic numerical functionality

# SwiftScript
# // something like
# string s = @sprintf([1:4])
# trace(s);

package require turbine 0.1
turbine::init

turbine::c::integer 1
turbine::c::integer 2
turbine::c::string  3
turbine::c::container 4 key integer

turbine::c::integer_set 1 1
turbine::c::integer_set 2 4

turbine::range 4 1 2
turbine::enumerate 3 4
turbine::trace 3

turbine::engine
turbine::finalize
puts OK
