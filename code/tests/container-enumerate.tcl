
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
    set s1 [ adlb::unique ]
    set s2 [ adlb::unique ]

    turbine::create_container $c integer
    turbine::create_string $s1 0
    turbine::store_string $s1 "hello"
    turbine::create_string $s2 0
    turbine::store_string $s2 "howdy"

    # insert <container> <subscript> <member>
    turbine::container_insert $c "0" $s1
    turbine::container_insert $c "1" $s2

    # Output member TDs for check script:
    puts "member1: $s1"
    puts "member2: $s2"

    set L [ adlb::enumerate $c subscripts 2 0 ]
    puts "subscripts: $L"

    set L [ adlb::enumerate $c members 2 0 ]
    puts "members: $L"

    set L [ adlb::enumerate $c dict 2 0 ]
    puts "dict: $L"

    set n [ adlb::enumerate $c count all 0 ]
    puts "count: $n"
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
