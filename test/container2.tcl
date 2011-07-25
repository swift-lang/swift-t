
# Test basic container functionality
# TCL version of container1

# SwiftScript
# file[] f1;
# file f2<"file1.txt">;
# file f3<"file2.txt">;
# f1[0] = f2;
# f2[1] = f3;
# // Print out contents of f1

package require turbine 0.1

proc rules { } {

    turbine::container_init 1 integer
    turbine::file_init 2 file1.txt
    turbine::file_init 3 file2.txt

    # set <container> <subscript> <member>
    turbine::container_insert 1 "0" 2
    turbine::container_insert 1 "1" 3

    set L [ turbine::container_list 1 ]
    puts "enumeration: $L"

    # This is not a real Turbine loop
    foreach subscript $L {
        set member [ turbine::container_get 1 $subscript ]
        puts "member: $member"
        set filename [ turbine::filename $member ]
        puts "filename: $filename"
    }
}

turbine::init 1
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
