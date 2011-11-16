
# Test basic container functionality

# SwiftScript
# file[] c;
# file f1<"file1.txt">;
# file f2<"file2.txt">;
# c[0] = f1;
# c[1] = f2;
# // Print out contents of c

package require turbine 0.1

proc rules { } {

    namespace import turbine::data_new
    namespace import turbine::integer_*
    namespace import turbine::arithmetic

    set c  [ data_new ]
    set f1 [ data_new ]
    set f2 [ data_new ]

    turbine::container_init $c integer
    turbine::file_init $f1 file1.txt
    turbine::file_init $f2 file2.txt

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

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
