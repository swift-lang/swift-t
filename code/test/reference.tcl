
# Test basic container reference functionality

# SwiftScript-ish
# int i = 37;
# int j = 41;
# int c[];
# // reference before insertion
# int* r1 = &c[i];
# int v1 = *r1;
# trace(v1);
# c[i] = j;
# // prints "trace: 41"

# // reference after insertion
# int k = 72;
# c[j] = k;
# int* r2 = &c[j]
# int v2 = *r2;
# trace(v2);
# // prints "trace: 72"

package require turbine 0.1

proc rules { } {

    set c [ turbine::data_new ]
    turbine::container_init $c integer

    set i [ turbine::literal integer 37 ]
    set j [ turbine::literal integer 41 ]
    set r1 [ turbine::data_new ]
    set v1 [ turbine::data_new ]
    turbine::integer_init $r1
    turbine::integer_init $v1
    turbine::f_reference no_stack "" "$c $i $r1"
    turbine::f_dereference_integer no_stack $v1 $r1
    turbine::trace no_stack "" $v1
    turbine::container_f_insert no_stack "" "$c $i $j"

    set k [ turbine::literal integer 72 ]
    turbine::container_f_insert no_stack "" "$c $j $k"
    set r2 [ turbine::data_new ]
    set v2 [ turbine::data_new ]
    turbine::integer_init $r2
    turbine::integer_init $v2
    turbine::f_reference no_stack "" "$c $j $r2"
    turbine::f_dereference_integer no_stack $v2 $r2
    turbine::trace no_stack "" $v2
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
