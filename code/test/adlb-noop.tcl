
# Flex ADLB but do nothing
# Nice to have for quick manual experiments

package require turbine 0.0.1
adlb::init 1 1

if [ adlb::amserver ] {
    set rc [ adlb::server ]
    if { $rc != $adlb::SUCCESS } {
        puts "adlb::server failed!"
        exit 1
    }
} else {}

adlb::finalize
puts OK
