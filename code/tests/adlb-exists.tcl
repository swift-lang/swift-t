
# Test what happens if we try to get something that does not exist

package require turbine 0.0.1
adlb::init 1 1

if [ adlb::amserver ] {
    adlb::server
} else {
    set z  0
    set d0 [ adlb::unique ]
    set d1 [ adlb::unique ]
    adlb::create $d1 $adlb::INTEGER
    set d2 [ adlb::unique ]
    adlb::create $d2 $adlb::INTEGER
    adlb::store $d2 $adlb::INTEGER 25
    set d3 [ adlb::unique ]
    adlb::create $d3 $adlb::INTEGER
    adlb::store $d3 $adlb::INTEGER 35
    adlb::close $d3
    set L [ list $z $d0 $d1 $d2 $d3 ]
    foreach d $L {
        if { [ adlb::exists $d ] } {
            puts "exists: $d"
        } else {
            puts "nope: $d"
        }
    }
}

adlb::finalize
puts OK

proc exit args {}
