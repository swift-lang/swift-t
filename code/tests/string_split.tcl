
# Test string split function

# Requires TURBINE_LOG=1

package require turbine 0.0.1

proc rules { } {

    turbine::create_string 11 0
    turbine::create_string 12 0
    turbine::create_string 13 0

    turbine::store_string 11 "hi how are you"

    turbine::create_container 18 integer
    turbine::split 0 18 11

    turbine::create_container 19 integer
    turbine::store_string 12 "/bin:/usr/evil name/p:/usr/bin"
    turbine::store_string 13 ":"
    turbine::split 0 19 { 12 13 }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
