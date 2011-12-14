
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

package require turbine 0.1

namespace import turbine::data_new
namespace import turbine::string_init
namespace import turbine::integer_*
namespace import turbine::literal
namespace import turbine::enumerate

proc f { stack o i } {
    puts "f: $i"
    set t [ integer_get $i ]
    if { $i == 0 } {
        integer_set $o 98
    } else {
        integer_set $o 72
    }
}

proc rules { } {

    set c [ data_new ]
    turbine::container_init $c integer
    set i1 [ literal integer 0 ]
    set i2 [ literal integer 1 ]
    set t1 [ data_new ]
    integer_init $t1
    set t2 [ data_new ]
    integer_init $t2

    turbine::call_composite no_stack f $t1 $i1
    turbine::call_composite no_stack f $t2 $i2

    turbine::container_f_insert no_stack "" "$c $i1 $t1"
    turbine::container_f_insert no_stack "" "$c $i2 $t2"

    set s [ data_new ]
    string_init $s

    turbine::enumerate no_stack $s $c
    turbine::trace no_stack "" $s
    turbine::close_container $c
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
