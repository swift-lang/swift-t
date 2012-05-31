
# Test basic container functionality

# SwiftScript
# file[] f1;
# file f2<"file1.txt">;
# file f3<"file2.txt">;
# f1[0] = f2;
# f2[1] = f3;
# // Print out contents of f1

package require turbine 0.0.1

proc rules { } {

    turbine::create_container 1 integer
    turbine::create_string 2
    turbine::create_string 3

    turbine::store_string 2 "string2"
    turbine::store_string 3 "string3"

    # set <container> <subscript> <member>
    turbine::container_insert 1 "0" 2
    turbine::container_insert 1 "1" 3

    set L [ adlb::enumerate 1 subscripts all 0 ]
    puts "enumeration: $L"

    # This is not a real Turbine loop
    foreach subscript $L {
        set member [ turbine::container_lookup 1 $subscript ]
        puts "member: $member"
        set s [ turbine::retrieve_string $member ]
        puts "string: $s"
    }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
