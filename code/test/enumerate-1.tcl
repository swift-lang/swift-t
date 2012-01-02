
# Test basic container close, enumerate functionality

# SwiftScript
# int c[];
# int i1=0, i2=1;
# int j1=98, j2=72;
# c[i1] = j1;
# c[i2] = j2;
# // Stringify/concatenate keys of container c
# string s = enumerate(c);
# trace(s);
# // prints "trace: 0 1"

package require turbine 0.0.1

namespace import turbine::data_new
namespace import turbine::string_init
namespace import turbine::literal
namespace import turbine::enumerate

proc rules { } {

    set c [ data_new ]
    turbine::container_init $c integer
    set i1 [ literal integer 0 ]
    set i2 [ literal integer 1 ]
    set j1 [ literal integer 98 ]
    set j2 [ literal integer 72 ]

    turbine::container_f_insert no_stack "" "$c $i1 $j1"
    turbine::container_f_insert no_stack "" "$c $i2 $j2"

    set s [ data_new ]
    string_init $s

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
