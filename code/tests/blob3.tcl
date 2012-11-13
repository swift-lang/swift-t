
# Test trace and basic string functionality

# SwiftScript (not testing data dependency)
# blob b1 = string_to_blob("hi");
# string s1 = blob_to_string(b1);
# trace(s1);

package require turbine 0.0.1

proc rules { } {

    set b [ adlb::unique ]
    turbine::create_blob $b 0

    set A [ adlb::unique ]
    turbine::create_container $A integer

    for { set i 0 } { $i < 3 } { incr i } {
        set x_value [ expr $i * 10 ]
        turbine::literal v float $x_value
        turbine::container_insert $A $i $v
    }
    turbine::close_datum $A

    turbine::blob_from_floats no_stack $b $A
    puts DONE
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
