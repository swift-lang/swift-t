
# Flex ADLB but do nothing
# Nice to have for quick manual experiments

puts hi
package require turbine 0.1
puts hi2
adlb::init 1 1
puts hi3

if [ adlb::amserver ] {
    adlb::server
} else {}

adlb::finalize
puts OK
