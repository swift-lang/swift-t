
# Test ADLB store/retrieve
# Does not use Turbine features

package require turbine 0.1

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
    for { set i $rank } { $i <= $count } { incr i $size } {
        adlb::create $i string:
        adlb::store $i string:data
    }
}

turbine::finalize

puts OK
