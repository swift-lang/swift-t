
# Test basic dereference functionality

# SwiftScript-ish
# int i = 3;
# int* r = &v;
# int v = *r;
# trace(*r)

package require turbine 0.1

proc rules { } {

    set i [ turbine::literal integer 3 ]
    set r [ turbine::literal integer $i ]
    set v [ turbine::data_new ]
    turbine::integer_init $v

    turbine::f_dereference_integer no_stack $v $r
    turbine::trace no_stack "" $v
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
