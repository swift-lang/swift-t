
# Test ADLB store/retrieve

package require turbine 0.1

namespace import turbine::string_*

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)

set count 10

if { ! [ adlb::amserver ] } {

    set size [ adlb::size ]
    set rank [ adlb::rank ]
    puts "MPI size: $size"
    for { set i $rank } { $i <= $count } { incr i $size } {
        adlb::create $i string:
        adlb::store $i string:data
    }
}

turbine::finalize

puts OK
