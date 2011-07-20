
# Does nothing
# Nice to have for quick experiments

package require turbine 0.1

# No rules
proc rules { } { }

turbine::init 1

turbine::start rules

turbine::finalize

puts OK
