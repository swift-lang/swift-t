
# Test trace and basic string functionality

# SwiftScript
# string s1 = "hi";
# string s2 = "bye";
# trace(s1,s2);

package require turbine 0.1

namespace import turbine::string_*

proc rules { } {

    string_init 1
    string_init 2
    # c::string 3

    string_set 1 "hi"
    string_set 2 "bye"

    set v1 [ string_get 1 ]
    set v2 [ string_get 2 ]

    puts -nonewline "result: "
    # Use 0 as stack frame
    turbine::trace 0 "" [ list 1 2 ]
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize

puts OK
