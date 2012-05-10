
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::create_integer 11
    turbine::create_integer 12
    turbine::create_integer 13

    turbine::store_integer 11 2
    turbine::store_integer 12 2

    set v1 [ turbine::get 11 ]
    set v2 [ turbine::get 12 ]
    set v3 [ expr $v1 + $v2 ]

    turbine::store_integer 13 $v3
    puts "result: $v3"
    set t [ adlb::typeof 13 ]
    puts "type: $t"
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
