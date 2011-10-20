
# Test trace and basic numerical functionality
# Uses Swift-0 plus() function

# SwiftScript
# x = 2+2;
# trace(x);

package require turbine 0.1

proc rules { } {

    turbine::integer_init 1
    turbine::integer_init 2
    turbine::integer_init 3

    turbine::integer_set 1 2
    turbine::integer_set 2 2

    set v1 [ turbine::integer_get 1 ]
    set v2 [ turbine::integer_get 2 ]

    turbine::rule 5 PLUS "1 2" 3 "tf: plus 1 2 3"
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)

turbine::start rules

turbine::finalize

puts OK
