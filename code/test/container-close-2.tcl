
# Test mid-level container close functionality
# Illustrates how to get container call backs

# SwiftScript
# int c[];
# int i1=0,  i2=1,  i3=2;
# int j1=98, j2=72, j3=88;
# c[i1] = j1;
# if (i3==0) { c[i2]=j2; }
# else       { c[i3]=j3; }
# string s = enumerate(c)
# trace(s);

package require turbine 0.0.1

namespace import turbine::data_new
namespace import turbine::string_init
namespace import turbine::integer_*
namespace import turbine::literal
namespace import turbine::enumerate
namespace import turbine::c::rule
namespace import turbine::c::rule_new

# This is like an if block
proc f { stack r c i j2 j3 } {
    puts "f: $i"
    if { $i == 0 } {
        turbine::container_f_insert no_stack "" "$c $i $j2"
    } else {
        turbine::container_f_insert no_stack "" "$c $i $j3"
    }
    turbine::container_branch_complete $r
}

proc rules { } {

    set c [ data_new ]
    turbine::container_init $c integer

    set i1 [ literal integer 0 ]
    set i2 [ literal integer 1 ]
    set i3 [ literal integer 2 ]

    set j1 [ literal integer 98 ]
    set j2 [ literal integer 72 ]
    set j3 [ literal integer 88 ]

    turbine::container_f_insert no_stack "" "$c $i1 $j1"

    set s [ data_new ]
    string_init $s

    set rule_id [ rule_new ]
    turbine::container_branch_post $rule_id $c
    turbine::rule $rule_id RULE_F $i3 "" \
        "tp: f no_stack $rule_id $c $i3 $j2 $j3"

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
