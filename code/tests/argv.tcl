
# Test trace and basic string functionality

# SwiftScript (not testing data dependency)
# blob b1 = string_to_blob("hi");
# string s1 = blob_to_string(b1);
# trace(s1);

package require turbine 0.0.1

proc rules { } {
    # Get first flagged parameter -F=valz
    turbine::literal k1 string "F"
    turbine::create_string 20
    turbine::argv_get no_stack 20 $k1
    turbine::trace no_stack {} 20

    # Get 2nd unflagged parameter
    turbine::literal k2 integer 2
    turbine::create_string 21
    turbine::argp_get no_stack 21 $k2
    turbine::trace no_stack {} 21

    # Check non-existant parameter -G
    turbine::literal k3 string "G"
    turbine::create_integer 22
    turbine::argv_contains no_stack 22 $k3
    turbine::trace no_stack {} 22

    # Get unflagged parameter count
    turbine::create_integer 23
    turbine::argc_get no_stack 23 {}
    turbine::trace no_stack {} 23

    turbine::argv_accept no_stack {} $k1
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
