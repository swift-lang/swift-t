
# Test basic readdata functionality

# SwiftScript
# file[] c;
# string s = "input.txt";
# c = readdata(s);
# foreach key in c {
#    trace(key)
#    trace(c[key])
# }

package require turbine 0.1
turbine_init

set c 1
turbine_container $c key integer
set s 2
turbine_string $s
turbine_string_set $s "test/data/input.txt"

turbine_readdata $c $s

turbine_loop loop1_body $c
proc loop1_body { key } {
    global c
    turbine_trace $key
    set t [ turbine_integer_get $key ]
    set value [ turbine_lookup $c key $t ]
    turbine_trace $value
}

turbine_engine

turbine_finalize
puts OK
