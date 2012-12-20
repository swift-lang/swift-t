
# Test rule on worker and control

package require turbine 0.0.1

proc worker_fn { x } {
    # Send to worker
    turbine::rule "worker" [ list ] $turbine::WORK "puts \"RAN RULE ON WORKER\""
    turbine::rule "after-x" [ list $x ] $turbine::WORK "puts \"RAN RULE AFTER X\""
    turbine::rule "local" [ list ] $turbine::LOCAL "puts \"RAN RULE LOCAL\""
    turbine::rule "engine" [ list ] $turbine::CONTROL "puts \"RAN RULE ON ENGINE\""
}

proc rules { } {
    turbine::allocate x integer 0

    turbine::rule "worker" "" ${turbine::WORK} "worker_fn $x"

    turbine::store_integer $x 1
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
