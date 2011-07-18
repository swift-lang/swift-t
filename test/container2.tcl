
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
turbine::init

turbine::c::container_init 1 key file
turbine::c::file 2 file1.txt
turbine::c::file 3 file2.txt

turbine::c::insert 1 key "0" 2
turbine::c::insert 1 key "1" 3

set L [ turbine::c::container_get 1 ]
puts "enumeration: $L"

# This is not a real Turbine loop
foreach id $L {
    set member [ turbine::c::lookup 1 "key" $id ]
    set filename [ turbine::c::filename $member ]
    puts "filename: $filename"
}

turbine::engine
turbine::finalize
puts OK
