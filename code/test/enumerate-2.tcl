
# Test basic container close, enumerate functionality

# SwiftScript
# int c[];
# int i1=0, i2=1;
# c[i1] = f(i1);
# c[i2] = f(i2);
# // Stringify/concatenate keys of container c
# string s = enumerate(c);
# trace(s);
# // prints "trace: 0 1"

package require turbine 0.0.1

proc f { stack o i } {
    puts "f: $i"
    set t [ turbine::get $i ]
    if { $i == 0 } {
        turbine::set_integer $o 98
    } else {
        turbine::set_integer $o 72
    }
}

proc rules { } {

    turbine::allocate_container c integer
    set i1 [ turbine::literal integer 0 ]
    set i2 [ turbine::literal integer 1 ]
    turbine::allocate t1 integer
    turbine::allocate t2 integer

    turbine::call_composite no_stack f $t1 $i1 $t1
    turbine::call_composite no_stack f $t2 $i2 $t1

    turbine::container_f_insert no_stack "" "$c $i1 $t1"
    turbine::container_f_insert no_stack "" "$c $i2 $t2"
    adlb::slot_drop $c

    turbine::allocate s string
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
