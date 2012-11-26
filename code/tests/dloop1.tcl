
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

    set stack [ adlb::unique ]

    set c [ adlb::unique ]

    turbine::container_init $stack string
    turbine::container_insert $stack "c" $c

    turbine::container_init $c integer
    set s1 [ adlb::unique ]
    turbine::string_init $s1
    turbine::string_set $s1 string1
    set s2 [ adlb::unique ]
    turbine::string_init $s2
    turbine::string_set $s2 string2

    turbine::container_insert $c "0" $s1
    turbine::container_insert $c "1" $s2

    # close container 
    adlb::slot_drop $c

    turbine::loop loop1_body $stack $c
}

proc loop1_body { stack container key } {

    puts "body: $stack $container $key"
    turbine::trace $key
    set t [ turbine::retrieve_integer $key ]
    set value [ turbine::container_get $container $t ]
    turbine::trace $value
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
