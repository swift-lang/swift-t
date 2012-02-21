
# Test basic container functionality

# SwiftScript
# file[] c;
# file f1<"file1.txt">;
# file f2<"file2.txt">;
# c[0] = f1;
# c[1] = f2;
# // Print out contents of c

package require turbine 0.0.1

proc rules { } {

    set c  [ adlb::unique ]
    set f1 [ adlb::unique ]
    set f2 [ adlb::unique ]

    turbine::create_container $c integer
    turbine::create_file $f1 file1.txt
    turbine::create_file $f2 file2.txt

    # insert <container> <subscript> <member>
    turbine::container_insert $c "0" $f1
    turbine::container_insert $c "1" $f2

    set L [ turbine::container_list $c ]
    puts "enumeration: $L"

    # This is not a real Turbine loop
    foreach subscript $L {
        set member [ turbine::container_get $c $subscript ]
        puts "member: $member"
        set filename [ turbine::filename $member ]
        puts "filename: $filename"
    }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
