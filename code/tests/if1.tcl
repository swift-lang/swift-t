# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

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

    # Create condition variable for "if"
    allocate c_1 integer
    rule $x   "f $x $c_1" name MYFUN_1 type $turbine::WORK
    rule $c_1 "if_1 $c_1 $a $x" name MYFUN_2
    rule $x   "g $x $b"   name MYFUN_3 type $turbine::WORK
}

proc if_1 { c a x } {

    # c is the condition variable
    set c_value [ retrieve_integer $c ]

    if $c_value {
        rule $x "h $x $a" \
            name IF_1_1 type $turbine::WORK
    } else {
        rule $x "j $x $a" \
            name IF_1_2 type $turbine::WORK
    }
    turbine::c::push
}

proc rules { } {

    turbine::allocate a integer
    turbine::allocate b integer
    turbine::literal x integer 3

    rule $x "myfun $a $b $x"

    set a_label [ literal string "a=" ]
    turbine::trace "" [ list $a_label $a ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
