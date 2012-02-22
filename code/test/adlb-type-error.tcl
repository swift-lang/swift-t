
# Test what happens if we try to get something of the wrong type

package require turbine 0.0.1
adlb::init 1 1

if [ adlb::amserver ] {
    adlb::server
} else {
    set d1 [ adlb::unique ]
    adlb::create $d1 $adlb::INTEGER
    adlb::store  $d1 $adlb::INTEGER 25
    set d2 [ adlb::unique ]
    adlb::create $d2 $adlb::INTEGER
    adlb::store  $d2 $adlb::INTEGER 26

    adlb::retrieve $d1 $adlb::INTEGER
    adlb::retrieve $d2 $adlb::STRING
}

adlb::finalize
puts OK

proc exit args {}
