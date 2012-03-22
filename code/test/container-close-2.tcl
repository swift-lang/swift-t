
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

    turbine::allocate_container c integer

    set i1 [ turbine::literal integer 0 ]
    set i2 [ turbine::literal integer 1 ]
    set i3 [ turbine::literal integer 2 ]

    set j1 [ turbine::literal integer 98 ]
    set j2 [ turbine::literal integer 72 ]
    set j3 [ turbine::literal integer 88 ]

    turbine::container_f_insert no_stack "" "$c $i1 $j1"

    turbine::allocate s string

    turbine::container_branch_post $rule_id $c
    turbine::rule RULE_F $i3 $turbine::LOCAL \
        "f no_stack $rule_id $c $i3 $j2 $j3"

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
