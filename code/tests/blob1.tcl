
# Test trace and basic string functionality

# SwiftScript (not testing data dependency)
# blob b1 = string_to_blob("hi");
# string s1 = blob_to_string(b1);
# trace(s1);

package require turbine 0.0.1

proc rules { } {

    turbine::create_blob 1

    turbine::store_blob_string 1 "hi"
    set v1 [ turbine::retrieve_blob_string 1 ]

    puts "result: $v1"
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
