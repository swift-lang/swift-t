
# Basic use of "if"

# SwiftScript:
#
# (int a, int b) myfun (int x) {
#   if (f(x)) {
#      a = h(x);
#   } else {
#      a = j(x);
#   }
#   b = g(x);
# }
#
# // try even or odd values here:
# int x = 4;
# int a, b;
# (a,b) = f(x);
# trace("a=",a);

package require turbine 0.0.1

namespace import turbine::*
namespace import turbine::c::rule*

proc f { x r } {
    # Leaf function
    # Set r to 1 if x is odd, else 0

    set x_value [ get $x ]
    set_integer $r [ expr $x_value % 2 ]
}

proc g { x r } {
    # Leaf function
    # Copy x into r

    set x_value [ get $x ]
    set_integer $r $x_value
}

proc h { x r } {
    # Leaf function
    # Copy x into r

    set x_value [ get $x ]
    set_integer $r $x_value
}

proc j { x r } {
    # Leaf function
    # Copy 0 into r

    set_integer $r 0
}

proc myfun { a b x } {

    # Create stack frame
    allocate_container stack string
    container_insert $stack "a" $a
    container_insert $stack "b" $b
    container_insert $stack "x" $x

    # Create condition variable for "if"
    allocate c_1 integer
    rule [ rule_new ] MYFUN_1 $x   $c_1 "tf: f $x $c_1"
    rule [ rule_new ] MYFUN_2 $c_1 { }  "tc: if_1 $stack $c_1"
    rule [ rule_new ] MYFUN_3 $x   $b   "tf: g $x $b"
}

proc if_1 { stack c } {

    # c is the condition variable
    set c_value [ get $c ]

    # Locate stack variables
    set a [ container_get $stack "a" ]
    set x [ container_get $stack "x" ]

    if $c_value {
        rule [ rule_new ] IF_1_1 $x $a "tf: h $x $a"
    } else {
        rule [ rule_new ] IF_1_2 $x $a "tf: j $x $a"
    }
    turbine::c::push
}

proc rules { } {

    turbine::allocate a integer
    turbine::allocate b integer
    turbine::literal x integer 3

    rule 1 A $x [ list $a $b ] "tp: myfun $a $b $x"

    set a_label [ literal string "a=" ]
    # Use 0 as stack frame
    turbine::trace 0 "" [ list $a_label $a ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
