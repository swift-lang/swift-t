
# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 0.0.1

proc rules { } {

    turbine::create_string 1
    turbine::create_string 2
    # c::string 3

    turbine::set_string 1 "hi"
    turbine::set_string 2 "bye"

    set v1 [ turbine::get 1 ]
    set v2 [ turbine::get 2 ]

    puts -nonewline "result: "
    # Use 0 as stack frame
    turbine::trace 0 "" [ list 1 2 ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
