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

# Based on STP test 238- fibonacci
# Copied here for stability

package require turbine 0.0.1
namespace import turbine::*

if { [ info exists env(TURBINE_TEST_PARAM_1) ] } {
    set N $env(TURBINE_TEST_PARAM_1)
} else {
    set N 7
}

proc fib { o n } {
    turbine::c::log function:fib
    set parent $stack
    allocate_container stack string
    container_insert $stack _parent $parent
    container_insert $stack n $n
    container_insert $stack o $o
    turbine::c::rule "$n" "if-0 $stack" [ name "fib-$o-$n" ]
}

proc if-0 { } {
    set n [ stack_lookup $stack n ]
    set o [ stack_lookup $stack o ]
    set n_value [ retrieve_integer $n ]
    if { $n_value } {
        set parent $stack
        allocate_container stack string
        container_insert $stack _parent $parent
        allocate __t0 integer
        container_insert $stack __t0 $__t0
        allocate __l0 integer
        store_integer $__l0 1
        turbine::minus_integer $stack [ list $__t0 ] [ list $n $__l0 ]
        turbine::c::rule if-1 "$__t0" $turbine::LOCAL $adlb::RANK_ANY 1 "if-1 $stack"
    } else {
        set parent $stack
        allocate_container stack string
        container_insert $stack _parent $parent
        turbine::set0 no_stack $o
    }
}

proc if-1 { stack } {
    set __t0 [ stack_lookup $stack __t0 ]
    set __pscope1 [ stack_lookup $stack _parent ]
    set n [ stack_lookup $__pscope1 n ]
    set o [ stack_lookup $__pscope1 o ]
    set __t0_value [ retrieve_integer $__t0 ]
    if { $__t0_value } {
        set parent $stack
        allocate_container stack string
        container_insert $stack _parent $parent
        allocate __l1 integer
        allocate __l2 integer
        allocate __l3 integer
        store_integer $__l3 1
        turbine::minus_integer $stack [ list $__l2 ] [ list $n $__l3 ]
        turbine::c::rule fib [ list $__l2 ] $turbine::LOCAL $adlb::RANK_ANY 1 "fib $stack $__l1 $__l2"
        allocate __l4 integer
        allocate __l5 integer
        allocate __l6 integer
        store_integer $__l6 2
        turbine::minus_integer $stack [ list $__l5 ] [ list $n $__l6 ]
        turbine::c::rule fib [ list $__l5 ] $turbine::LOCAL $adlb::RANK_ANY 1 "fib $stack $__l4 $__l5"
        turbine::plus_integer $stack [ list $o ] [ list $__l1 $__l4 ]
    } else {
        set parent $stack
        allocate_container stack string
        container_insert $stack _parent $parent
	turbine::set1 no_stack $o
    }
}

proc rules {  } {
    turbine::c::log function:rules
    allocate_container stack string
    allocate __l0 integer
    allocate __l1 integer
    global N
    puts "N: $N"
    store_integer $__l1 $N
    turbine::c::rule fib [ list $__l1 ] $turbine::LOCAL $adlb::RANK_ANY 1 "fib $stack $__l0 $__l1"
    turbine::trace $stack [ list ] [ list $__l0 ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
