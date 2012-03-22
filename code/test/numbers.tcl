
# Test trace and basic numerical functionality

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.0.1

proc rules { } {

    turbine::create_integer 1
    turbine::create_integer 2
    turbine::create_integer 3

    turbine::set_integer 1 2
    turbine::set_integer 2 2

    set v1 [ turbine::get 1 ]
    set v2 [ turbine::get 2 ]
    set v3 [ expr $v1 + $v2 ]

    turbine::set_integer 3 $v3
    puts "result: $v3"
    set t [ adlb::typeof 3 ]
    puts "type: $t"
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
