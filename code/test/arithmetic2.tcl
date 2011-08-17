
# Test trace and basic numerical functionality

# SwiftScript
# x = (3+5)*(3+5);
# trace(x);

package require turbine 0.1

proc rules { } {

    namespace import turbine::data_new
    namespace import turbine::integer_*
    namespace import turbine::arithmetic

    set t3 [ data_new ]
    set t5 [ data_new ]
    set x  [ data_new ]

    integer_init $t3
    integer_init $t5
    integer_init $x

    integer_set $t3 3
    integer_set $t5 5

    arithmetic $x "(_+_)*(_+_)" $t3 $t5 $t3 $t5
    turbine::trace $x
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)
turbine::start rules
turbine::finalize

puts OK
