
# Does nothing
# Nice to have for quick experiments

package require turbine 0.1

# No rules
proc rules { } { }

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)

turbine::start rules

turbine::finalize

puts OK
