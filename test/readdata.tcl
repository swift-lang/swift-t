
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
turbine::init

set c 1
turbine::c::container $c key integer
set s 2
turbine::c::string $s
turbine::c::string_set $s "test/data/input.txt"

turbine::readdata $c $s

turbine::loop loop1_body $c
proc loop1_body { key } {
    global c
    turbine::trace $key
    set t [ turbine::c::integer_get $key ]
    set value [ turbine::c::lookup $c key $t ]
    turbine::trace $value
}

turbine::engine

turbine::finalize
puts OK
