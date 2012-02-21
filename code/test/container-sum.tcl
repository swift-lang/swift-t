
# Test basic container functionality

# SwiftScript
# int[] A;
# A[0] = 2;
# A[1] = 4;
# A[2] = -1;
# A[3] = 3;
# // Print out sum of array
# trace(sum(A));

package require turbine 0.0.1

proc rules { } {

    turbine::create_container 1 integer
    adlb::slot_create 1

    turbine::create_integer 2
    turbine::set_integer 2 12345
    turbine::create_integer 3
    turbine::set_integer 3 4
    turbine::create_integer 4
    turbine::set_integer 4 -1
    turbine::create_integer 5
    turbine::set_integer 5 3
    # 12345 + 4 - 1 + 3 = 12351

    # set <container> <subscript> <member>
    turbine::container_immediate_insert 1 "0" 2
    turbine::container_immediate_insert 1 "1" 3
    turbine::container_immediate_insert 1 "2" 4
    turbine::container_immediate_insert 1 "3" 5
    # close the container
    adlb::slot_drop 1

    # initialise the result
    turbine::create_integer 6

    turbine::sum_integer NO_STACK [ list 6 ] [ list 1 ]

    # trace the result
    turbine::trace NO_STACK [ list ] [ list 6]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
