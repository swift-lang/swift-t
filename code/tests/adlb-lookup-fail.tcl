
# Test what happens if we try to lookup something that does not exist

package require turbine 0.0.1
adlb::init 1 1

if [ adlb::amserver ] {
    adlb::server
} else {
    adlb::create 3 $adlb::CONTAINER integer
    set result [ adlb::lookup 3 2 ]
    puts "lookup: $result"
}

adlb::finalize
puts OK
