
# Test ADLB store/retrieve

package require turbine 0.1

namespace import turbine::string_*

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)

if { ! [ adlb::amserver ] } {

    adlb::create 12 string
    adlb::store 12 string:data
}

turbine::finalize

puts OK
