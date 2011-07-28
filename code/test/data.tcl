
# Turbine data abstraction layer

namespace eval turbine::data {

    proc string_init { id } {
        c::string $id
    }

    proc string_set { id value } {
        c::string_set $id $value
    }

    proc string_get { id } {
        c::string_get $id
    }

}
