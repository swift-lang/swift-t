
# Test basic container functionality
# TCL version of container1

# SwiftScript
# string[] c;
# int key1 = 0;
# string s1 = "string1";
# int key2 = 1
# string s2 = "string2";
# c[key1] = s1;
# c[key2] = s2;
# foreach key in c
#   trace(key, c[key]);

package require turbine 0.1
turbine::init

set c 1
turbine::c::container_init $c key integer
set s1 3
turbine::c::string_init $s1
turbine::c::string_set $s1 string1
turbine::c::close $s1
set s2 5
turbine::c::string_init $s2
turbine::c::string_set $s2 string2
turbine::c::close $s2

turbine::c::insert $c key 0 $s1
turbine::c::insert $c key 1 $s2

turbine::c::close $c

turbine::loop loop1_body $c
proc loop1_body { key } {
    global c
    puts "body: $key"
    turbine::trace $key
    set t [ turbine::c::integer_get $key ]
    set value [ turbine::c::lookup $c key $t ]
    turbine::trace $value
}

# set L [ turbine::c::container_get 1 ]
# puts "enumeration: $L"

# This is not a real Turbine loop
# foreach id $L {
#     set member [ turbine::c::lookup 1 "key" $id ]
#     set filename [ turbine::c::filename $member ]
#     puts "filename: $filename"
# }

turbine::engine
turbine::finalize
puts OK
