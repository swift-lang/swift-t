
# Test basic container functionality
# TCL version of container1

# SwiftScript
# file[] c;
# int key1 = 0;
# file file1<"file1.txt">;
# int key2 = 1
# file file2<"file2.txt">;
# c[key1] = file1;
# c[key2] = file2;
# foreach key in c
#   trace(key, c[key]);

package require turbine 0.1
turbine_c_init

set c 1
turbine_container $c key
set file1 3
turbine_file $file1 file1.txt
turbine_close $file1
set file2 5
turbine_file $file2 file2.txt
turbine_close $file2

turbine_insert $c key 0 $file1
turbine_insert $c key 1 $file2

turbine_close $c

turbine_loop loop1_body $c
proc loop1_body { key } {
    global c
    puts "body: $key"
    turbine_trace $key
    set t [ turbine_integer_get $key ]
    set value [ turbine_lookup $c key $t ]
    turbine_trace $value
}

# set L [ turbine_container_get 1 ]
# puts "enumeration: $L"

# This is not a real Turbine loop
# foreach id $L {
#     set member [ turbine_lookup 1 "key" $id ]
#     set filename [ turbine_filename $member ]
#     puts "filename: $filename"
# }

turbine_engine
turbine_finalize
puts OK
