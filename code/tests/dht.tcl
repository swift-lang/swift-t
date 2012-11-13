
# Test ADLB store/retrieve
# Does not use Turbine features

package require turbine 0.0.1

namespace import turbine::string_*

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)

if { ! [ adlb::amserver ] } {

    set count 10
    if { [ info exists env(COUNT) ] > 0 } {
        set count $env(COUNT)
    }

    set size [ adlb::size ]
    set rank [ adlb::rank ]
    if { $rank == 0 } {
        puts "COUNT: $count"
    }

    # puts "MPI size: $size"
    set r [ expr $rank + 1 ]
    for { set i $r } { $i <= $count } { incr i $size } {
        adlb::create $i $adlb::STRING 0
        adlb::store $i $adlb::STRING "data"
    }
} else {
    adlb::server
}

turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
