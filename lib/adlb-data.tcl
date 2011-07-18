
# Turbine data abstraction layer

namespace eval turbine::adlb::data {

    namespace export string_init string_set string_get

    proc string_init { id } {
        adlb::store $id "string:UNSET"
    }

    proc string_set { id value } {
        adlb::store $id "string:$value"
    }

    proc string_get { id } {
        puts "get $id"
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        set result [ string range $s $i end ]
        return $result
    }
}
