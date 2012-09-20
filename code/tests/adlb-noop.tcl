
# Flex ADLB but do nothing
# Nice to have for quick manual experiments

package require turbine 0.0.1

puts NOOP

adlb::init 1 1

if [ adlb::amserver ] {
    puts "SERVER"
    adlb::server
} else {}

adlb::finalize
puts OK
