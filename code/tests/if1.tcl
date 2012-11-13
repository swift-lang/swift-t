
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

    set x_value [ retrieve_integer $x ]
    store_integer $r [ expr $x_value % 2 ]
}

proc g { x r } {
    # Leaf function
    # Copy x into r

    set x_value [ retrieve_integer $x ]
    store_integer $r $x_value
}

proc h { x r } {
    # Leaf function
    # Copy x into r

    set x_value [ retrieve_integer $x ]
    store_integer $r $x_value
}

proc j { x r } {
    # Leaf function
    # Copy 0 into r

    store_integer $r 0
}

proc myfun { a b x } {

    # Create stack frame
    allocate_container stack string
    container_insert $stack "a" $a
    container_insert $stack "b" $b
    container_insert $stack "x" $x

    # Create condition variable for "if"
    allocate c_1 integer 0
    rule MYFUN_1 $x   $turbine::WORK    "f $x $c_1"
    rule MYFUN_2 $c_1 $turbine::CONTROL "if_1 $stack $c_1"
    rule MYFUN_3 $x   $turbine::WORK    "g $x $b"
}

proc if_1 { stack c } {

    # c is the condition variable
    set c_value [ retrieve_integer $c ]

    # Locate stack variables
    set a [ container_lookup $stack "a" ]
    set x [ container_lookup $stack "x" ]

    if $c_value {
        rule IF_1_1 $x $turbine::WORK "h $x $a"
    } else {
        rule IF_1_2 $x $turbine::WORK "j $x $a"
    }
    turbine::c::push
}

proc rules { } {

    turbine::allocate a integer 0
    turbine::allocate b integer 0
    turbine::literal x integer 3

    rule A $x $turbine::CONTROL "myfun $a $b $x"

    set a_label [ literal string "a=" ]
    # Use 0 as stack frame
    turbine::trace 0 "" [ list $a_label $a ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
