
# Based on STP test 238- fibonacci
# Copied here for stability

package require turbine 0.0.1
namespace import turbine::*

if { [ info exists env(TURBINE_TEST_PARAM_1) ] } {
    set N $env(TURBINE_TEST_PARAM_1)
} else {
    set N 4
}

proc fib { stack o n } {
    turbine::c::log function:fib
    set parent $stack
    set stack [ data_new stack ]
    container_init $stack string
    container_insert $stack _parent $parent
    container_insert $stack n $n
    container_insert $stack o $o
    set rule_id [ turbine::c::rule_new ]
    turbine::c::rule $rule_id if-0 "$n" "" "tc: if-0 $stack"
}

proc if-0 { stack } {
    set n [ stack_lookup $stack n ]
    set o [ stack_lookup $stack o ]
    set n_value [ integer_get $n ]
    if { $n_value } {
        set parent $stack
        set stack [ data_new stack ]
        container_init $stack string
        container_insert $stack _parent $parent
        set __t0 [ data_new __t0 ]
        integer_init $__t0
        container_insert $stack __t0 $__t0
        set __l0 [ data_new __l0 ]
        integer_init $__l0
        integer_set $__l0 1
        turbine::minus $stack [ list $__t0 ] [ list $n $__l0 ]
        set rule_id [ turbine::c::rule_new ]
        turbine::c::rule $rule_id if-1 "$__t0" "" "tc: if-1 $stack"
    } else {
        set parent $stack
        set stack [ data_new stack ]
        container_init $stack string
        container_insert $stack _parent $parent
        integer_set $o 0
    }
}

proc if-1 { stack } {
    set __t0 [ stack_lookup $stack __t0 ]
    set __pscope1 [ stack_lookup $stack _parent ]
    set n [ stack_lookup $__pscope1 n ]
    set o [ stack_lookup $__pscope1 o ]
    set __t0_value [ integer_get $__t0 ]
    if { $__t0_value } {
        set parent $stack
        set stack [ data_new stack ]
        container_init $stack string
        container_insert $stack _parent $parent
        set __l1 [ data_new __l1 ]
        integer_init $__l1
        set __l2 [ data_new __l2 ]
        integer_init $__l2
        set __l3 [ data_new __l3 ]
        integer_init $__l3
        integer_set $__l3 1
        turbine::minus $stack [ list $__l2 ] [ list $n $__l3 ]
        set rule_id [ turbine::c::rule_new ]
        turbine::c::rule $rule_id fib [ list $__l2 ] [ list $__l1 ] "tp: fib $stack $__l1 $__l2"
        set __l4 [ data_new __l4 ]
        integer_init $__l4
        set __l5 [ data_new __l5 ]
        integer_init $__l5
        set __l6 [ data_new __l6 ]
        integer_init $__l6
        integer_set $__l6 2
        turbine::minus $stack [ list $__l5 ] [ list $n $__l6 ]
        set rule_id [ turbine::c::rule_new ]
        turbine::c::rule $rule_id fib [ list $__l5 ] [ list $__l4 ] "tp: fib $stack $__l4 $__l5"
        turbine::plus $stack [ list $o ] [ list $__l1 $__l4 ]
    } else {
        set parent $stack
        set stack [ data_new stack ]
        container_init $stack string
        container_insert $stack _parent $parent
	turbine::set1 no_stack $o
    }
}

proc rules {  } {
    turbine::c::log function:rules
    set stack [ data_new stack ]
    container_init $stack string
    set __l0 [ data_new __l0 ]
    integer_init $__l0
    set __l1 [ data_new __l1 ]
    integer_init $__l1
    global N
    puts "N: $N"
    integer_set $__l1 $N
    set rule_id [ turbine::c::rule_new ]
    turbine::c::rule $rule_id fib [ list $__l1 ] [ list $__l0 ] "tp: fib $stack $__l0 $__l1"
    turbine::trace $stack [ list ] [ list $__l0 ]
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

