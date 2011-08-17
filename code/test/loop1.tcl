
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

proc rules { } {

    namespace import turbine::*

    set stack [ data_new ]
    set c [ data_new ]

    container_init $stack string
    container_insert $stack "c" $c

    container_init $c integer
    set s1 [ data_new ]
    string_init $s1
    string_set $s1 string1
    set s2 [ data_new ]
    string_init $s2
    string_set $s2 string2

    container_insert $c "0" $s1
    container_insert $c "1" $s2

    close_container $c

    turbine::loop loop1_body $stack $c
}

proc loop1_body { stack container key } {

    namespace import turbine::*

    puts "body: $stack $container $key"
    turbine::trace $key
    set t [ integer_get $key ]
    set value [ container_get $container $t ]
    turbine::trace $value
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
