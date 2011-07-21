
# Turbine data abstraction layer

namespace eval turbine {

    namespace export                      \
        string_init string_set string_get \
        file_init   file_set   filename

    proc string_init { id } {
        adlb::create $id "string:"
        turbine::c::declare $id
    }

    proc string_set { id value } {
        close_dataset $id "string:$value"
    }

    proc string_get { id } {
        puts "get $id"
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        set result [ string range $s $i end ]
        return $result
    }

    proc integer_init { id } {
        adlb::create $id "integer:"
        turbine::c::declare $id
    }

    proc integer_set { id value } {
        close_dataset $id "integer:$value"
    }

    proc integer_get { id } {
        puts "get $id"
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        incr i
        set result [ string range $s $i end ]
        return $result
    }

    proc file_init { id path } {
        adlb::create $id "file:$path"
        turbine::c::declare $id
    }

    proc file_set { id } {
        close_dataset $id "file:close"
    }

    proc filename { id } {
        debug "filename($id)"
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        set i [ expr $i + 1 ]
        set result [ string range $s $i end ]
        return $result
    }

    proc close_dataset { id value } {
        global WORK_TYPE
        adlb::store $id $value
        set ranks [ adlb::close $id ]
        foreach rank $ranks {
            puts "notify: $rank"
            adlb::put $rank $WORK_TYPE(CONTROL) "close $id"
        }
    }
}
