
# Test basic readdata functionality

# SwiftScript
# string[] c;
# string s = "input.txt";
# c = readdata(s);
# foreach key in c {
#    trace(key)
#    trace(c[key])
# }

package require turbine 0.1

proc rules { } {

    set c 1
    turbine::container_init $c integer
    set s 2
    turbine::string_init $s
    turbine::string_set $s "test/data/input.txt"

    turbine::readdata $c $s
    turbine::loop loop1_body none $c
}

proc loop1_body { parent container key } {
    turbine::trace $parent "" $key
    set t [ turbine::integer_get $key ]
    set value [ turbine::container_get $container $t ]
    turbine::trace $parent "" $value
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize
puts OK
