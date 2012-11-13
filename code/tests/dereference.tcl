
# Test basic dereference functionality

# SwiftScript-ish
# int i = 3;
# int* r = &v;
# int v = *r;
# trace(*r)

package require turbine 0.0.1

proc rules { } {

    turbine::literal i integer 3
    turbine::literal r integer $i
    turbine::allocate v integer 0

    turbine::f_dereference_integer no_stack $v $r
    turbine::trace no_stack "" $v
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
