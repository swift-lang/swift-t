
# Test what happens if we try to get something that does not exist

package require turbine 0.0.1
adlb::init 1 1

if [ adlb::amserver ] {
    adlb::server
} else {
    puts "get: `[ adlb::retrieve 1 ]'"
}

adlb::finalize
puts OK
