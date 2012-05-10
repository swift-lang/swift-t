
# Test trace and basic numerical functionality

# SwiftScript
# int i = 1;
# int j = 4;
# int c[] = [i:j];
# string s = @sprintf(c)
# trace(s);

package require turbine 0.0.1

proc rules { } {
    turbine::allocate i integer
    turbine::allocate j integer
    turbine::allocate_container c integer

    turbine::store_integer $i 1
    turbine::store_integer $j 4

    turbine::range NO_STACK $c [ list $i $j ]
    turbine::loop loop1_body none $c
}

proc loop1_body { parent container key } {
    puts "loop1_body: $key"
    set t [ turbine::retrieve $key ]
    set member [ turbine::container_lookup $container $t ]
    # set value [ turbine::retrieve_integer $member ]
    turbine::trace $parent "" [ list $key $member ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
