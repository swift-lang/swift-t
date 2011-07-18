
# Flex Turbine+ADLB but do nothing
# Nice to have for quick manual experiments

package require turbine 0.1
adlb::init 1
turbine::init

turbine::finalize
adlb::finalize
puts OK
