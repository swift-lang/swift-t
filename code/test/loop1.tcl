
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

package require turbine 0.0.1

proc rules { } {

    namespace import turbine::*

    allocate_container stack string
    allocate_container c    integer
    container_insert $stack "c" $c

    literal s1 string "string1"
    literal s2 string "string2"

    container_insert $c "0" $s1
    container_insert $c "1" $s2

    close_container $c

    turbine::loop loop1_body $stack $c
}

proc loop1_body { parent container key } {

    namespace import turbine::*

    puts "body: $parent $container $key"
    turbine::trace $parent "" $key
    set t [ get $key ]
    set value [ container_get $container $t ]
    turbine::trace $parent "" $value
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
