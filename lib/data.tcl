
# Turbine data abstraction layer

namespace eval turbine::data {

    namespace export string_init string_set string_get

    proc string_init { id } {
        turbine::c::string_init $id
    }

    proc string_set { id value } {
        turbine::c::string_set $id $value
    }

    proc string_get { id } {
        turbine::c::string_get $id
    }
}
