
# Test basic container functionality
# TCL version of container1

package require turbine 0.1
turbine_init

turbine_container 1 key
turbine_file 2 file1.txt
turbine_file 3 file2.txt

turbine_insert 1 key "0" 2
turbine_insert 1 key "1" 3

set L [ turbine_enumerate 1 ]
puts "enumeration: $L"

foreach id $L {
    set member [ turbine_lookup 1 "key" $id ]
    set filename [ turbine_filename $member ]
    puts "filename: $filename"
}

turbine_engine
turbine_finalize
puts OK
