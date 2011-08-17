
# Turbine data abstraction layer

namespace eval turbine {

    namespace export                                  \
        data_new                                      \
        string_init      string_set    string_get     \
        integer_init     integer_set   integer_get    \
        container_init   container_get container_list \
        container_insert close_container              \
        file_init        file_set      filename

    proc typeof { id } {
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        incr i -1
        set result [ string range $s 0 $i ]
        return $result
    }

    proc data_new { } {
        return [ adlb::unique ]
    }

    proc string_init { id } {
        adlb::create $id "string:"
        turbine::c::declare $id
    }

    proc string_set { id value } {
        close_dataset $id "string:$value"
    }

    proc string_get { id } {
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        incr i
        set result [ string range $s $i end ]
        return $result
    }

    proc integer_init { id } {
        debug "integer_init: <$id>"
        adlb::create $id "integer:"
        turbine::c::declare $id
    }

    proc integer_set { id value } {
        debug "integer_set: <$id>=$value"
        close_dataset $id "integer:$value"
    }

    proc integer_get { id } {
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        incr i
        set result [ string range $s $i end ]
        debug "integer_get: <$id>=$result"
        return $result
    }

    proc container_init { id type } {
        adlb::create $id "container:$type"
        turbine::c::declare $id
    }

    proc container_insert { id subscript value } {
        adlb::insert $id $subscript $value
    }

    proc container_get { id subscript } {
        set s [ adlb::lookup $id $subscript ]
        return $s
    }

    proc container_typeof { id } {
        set s [ adlb::container_typeof $id ]
        return $s
    }

    proc container_list { id } {
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
            debug "notify: $rank"
            adlb::put $rank $WORK_TYPE(CONTROL) "close $id"
        }
    }

    proc close_container { id } {
        global WORK_TYPE
        set ranks [ adlb::close $id ]
        foreach rank $ranks {
            debug "notify: $rank"
            adlb::put $rank $WORK_TYPE(CONTROL) "close $id"
        }
    }
}
