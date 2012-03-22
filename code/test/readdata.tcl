
# Test basic readdata functionality

# SwiftScript
# string[] c;
# string s = "input.txt";
# c = readdata(s);
# foreach key in c {
#    trace(key)
#    trace(c[key])
# }

package require turbine 0.0.1

proc rules { } {

    set c 1
    turbine::create_container $c integer
    turbine::literal s string "test/data/input.txt"

    turbine::readdata $c $s
    turbine::loop loop1_body none $c
}

proc loop1_body { parent container key } {
    turbine::trace $parent "" $key
    set t [ turbine::get $key ]
    set value [ turbine::container_get $container $t ]
    turbine::trace $parent "" $value
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
