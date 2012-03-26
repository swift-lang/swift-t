
# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 0.0.1

proc rules { } {

    turbine::create_string 11
    turbine::create_string 12
    turbine::create_string 13
    # c::string 3

    turbine::set_string 11 "hi how are you"

    turbine::create_container 18 integer
    turbine::split 0 18 11

    turbine::create_container 19 integer
    turbine::set_string 12 "/bin:/usr/evil name/p:/usr/bin"
    turbine::set_string 13 ":"
    turbine::split 0 19 { 12 13 }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
