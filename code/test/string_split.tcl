
# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 0.0.1

proc rules { } {

    turbine::create_string 1
    turbine::create_string 2
    turbine::create_string 3
    # c::string 3

    turbine::set_string 1 "hi how are you"

    turbine::create_container 8 integer
    turbine::split 0 8 1

    turbine::create_container 9 integer
    turbine::set_string 2 "/bin:/usr/evil name/p:/usr/bin"
    turbine::set_string 3 ":"
    turbine::split 0 9 { 2 3 }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
