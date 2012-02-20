
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::integer_init 1
    turbine::integer_init 2
    turbine::integer_init 3

    turbine::integer_set 1 2
    turbine::integer_set 2 2

    set v1 [ turbine::integer_get 1 ]
    set v2 [ turbine::integer_get 2 ]
    set v3 [ expr $v1 + $v2 ]

    turbine::integer_set 3 $v3
    puts "result: $v3"
    set t [ adlb::typeof 3 ]
    puts "type: $t"
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
