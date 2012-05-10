
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

    turbine::create_container 11 integer
    # adlb::slot_create 1

    turbine::create_integer 12
    turbine::store_integer 12 12345
    turbine::create_integer 13
    turbine::store_integer 13 4
    turbine::create_integer 14
    turbine::store_integer 14 -1
    turbine::create_integer 15
    turbine::store_integer 15 3
    # 12345 + 4 - 1 + 3 = 12351

    # set <container> <subscript> <member>
    turbine::container_immediate_insert 11 "0" 12
    turbine::container_immediate_insert 11 "1" 13
    turbine::container_immediate_insert 11 "2" 14
    turbine::container_immediate_insert 11 "3" 15
    # close the container
    adlb::slot_drop 11

    # initialise the result
    turbine::create_integer 16

    turbine::sum_integer NO_STACK [ list 16 ] [ list 11 ]

    # trace the result
    turbine::trace NO_STACK [ list ] [ list 16 ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
