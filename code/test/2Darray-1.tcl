
# Test nested container operation

# SwiftScript-ish
# int A[][];
# int i = 37;
# int j = 41;
# int k = 59;
# A[i][j] = k;

package require turbine 0.1

proc rules { } {

    # By analysis, we determine that A has one insertion
    # in this scope: an anonymous container t1
    # By analysis, we determine that t1 has one insertion in this
    # scope
    set A [ turbine::data_new ]
    turbine::container_init $A integer
    set t1 [ turbine::data_new ]
    turbine::container_init $t1 integer

    set i [ turbine::literal integer 37 ]
    set j [ turbine::literal integer 41 ]
    set k [ turbine::literal integer 59 ]

    set r1 [ turbine::data_new ]
    set v1 [ turbine::data_new ]
    turbine::integer_init $r1
    turbine::integer_init $v1
    turbine::f_reference no_stack "" "$A $i $r1"
    turbine::container_f_insert no_stack "" "$A $i $t1"
    turbine::f_container_reference_insert no_stack "" "$r1 $A $i $t1"
    turbine::container_f_insert no_stack "" "$t1 $j $k"
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
