
# Test low-level container close functionality

# SwiftScript
# int c[];
# int i1=0, i2=1;
# int j1=98, j2=72;
# c[i1] = j1;
# c[i2] = j2;
# string s = enumerate(c)
# trace(s);

package require turbine 0.0.1

proc rules { } {

    turbine::allocate_container c integer

    set i1 [ turbine::literal integer 0 ]
    set i2 [ turbine::literal integer 1 ]

    set j1 [ turbine::literal integer 98 ]
    set j2 [ turbine::literal integer 72 ]

    turbine::container_f_insert no_stack "" "$c $i1 $j1"
    turbine::container_f_insert no_stack "" "$c $i2 $j2"
    adlb::slot_drop $c

    turbine::allocate s string 0

    turbine::enumerate no_stack $s $c
    turbine::trace no_stack "" $s
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
