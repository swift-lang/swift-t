
# Test nested container operation

# SwiftScript-ish
# int A[][];
# int i = 37;
# int j = 41;
# int k = 59;
# A[i][j] = k;

# This could be implemented without references but is
# done with references here for testing

package require turbine 0.0.1

proc rules { } {

    # By analysis, we determine that A has one insertion
    # in this scope: an anonymous container t1
    # By analysis, we determine that t1 has one insertion in this
    # scope
    turbine::allocate_container A integer
    turbine::allocate_container t1 integer

    turbine::literal i integer 37
    turbine::literal j integer 41
    turbine::literal k integer 59

    turbine::allocate r1 integer
    turbine::f_reference no_stack "" "$A $i $r1"
    turbine::container_f_insert no_stack "" "$A $i $t1"
    turbine::f_container_reference_insert no_stack "" "$r1 $j $t1 $A"
    # turbine::container_f_insert no_stack "" "$t1 $j $k"
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
