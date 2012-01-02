
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

    namespace import turbine::data_new
    namespace import turbine::integer_*
    namespace import turbine::arithmetic

    set c  [ data_new ]
    set x1 [ data_new ]
    set x2 [ data_new ]
    set i1 [ data_new ]
    set i2 [ data_new ]
    set v1 [ data_new ]
    set v2 [ data_new ]

    turbine::container_init $c integer
    turbine::integer_init $x1
    turbine::integer_init $x2
    turbine::integer_init $i1
    turbine::integer_init $i2
    turbine::integer_init $v1
    turbine::integer_init $v2

    turbine::integer_set $x1 14
    turbine::integer_set $x2 15

    # We pretend that we know the indices here
    # insert <container> <subscript> <member>
    turbine::container_insert $c 34 $x1
    turbine::container_insert $c 35 $x2

    set L [ turbine::container_list $c ]
    puts "enumeration: $L"

    turbine::container_f_get no_stack $v1 "$c $i1"
    turbine::trace no_stack "" $v1
    turbine::container_f_get no_stack $v2 "$c $i2"
    turbine::trace no_stack "" $v2

    turbine::integer_set $i1 34
    turbine::integer_set $i2 35
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
