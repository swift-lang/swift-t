
# Test basic container functionality

# SwiftScript
# int c[];
# int x1 = 14;
# int x2 = 15;
# int i1;
# int i2;
# c[34] = x1;
# c[35] = x2;
# int v1 = c[i1];
# int v2 = c[i2];
# i1 = 34;
# i2 = 35;
# trace(v1);
# trace(v2);

package require turbine 0.0.1

proc rules { } {

    namespace import adlb::unique

    turbine::allocate_container c integer
    turbine::allocate x1 integer
    turbine::allocate x2 integer
    turbine::allocate i1 integer
    turbine::allocate i2 integer
    turbine::allocate v1 integer
    turbine::allocate v2 integer

    turbine::store_integer $x1 14
    turbine::store_integer $x2 15

    # We pretend that we know the indices here
    # insert <container> <subscript> <member>
    turbine::container_insert $c 34 $x1
    turbine::container_insert $c 35 $x2

    set L [ turbine::container_list $c ]
    puts "enumeration: $L"

    turbine::container_f_get_integer no_stack $v1 "$c $i1"
    turbine::trace no_stack "" $v1
    turbine::container_f_get_integer no_stack $v2 "$c $i2"
    turbine::trace no_stack "" $v2

    turbine::store_integer $i1 34
    turbine::store_integer $i2 35
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
