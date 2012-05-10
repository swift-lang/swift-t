
# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 0.0.1

proc rules { } {

    turbine::create_string 11
    turbine::create_string 12
    # c::string 3

    turbine::store_string 11 "hi"
    turbine::store_string 12 "bye"

    set v1 [ turbine::get 11 ]
    set v2 [ turbine::get 12 ]

    puts -nonewline "result: "
    # Use 0 as stack frame
    turbine::trace 0 "" [ list 11 12 ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
