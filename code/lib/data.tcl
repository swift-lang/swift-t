
# Turbine data abstraction layer

namespace eval turbine {

    namespace export                                  \
        data_new                                      \
        string_init      string_set    string_get     \
        integer_init     integer_set   integer_get    \
        container_init   container_get container_list \
        container_insert close_container              \
        file_init        file_set      filename

    # namespace import adlb::INTEGER adlb::STRING

#     proc typeof { id } {
#         set s [ adlb::retrieve $id ]
#         set i [ string first : $s ]
#         incr i -1
#         set result [ string range $s 0 $i ]
#         return $result
#     }

    # Obtain unique TD
    # If given an argument, sets the variable by that name
    # and a log message is reported
    proc data_new { args } {
        set u [ adlb::unique ]
        if { [ string length $args ] } {
            log "variable: $args=<$u>"
            upvar 1 $args v
            set v $u
        }
        return $u
    }

    proc integer_init { id } {
        debug "integer_init: <$id>"
        adlb::create $id "integer"
        turbine::c::declare $id
    }

    proc integer_set { id value } {
        log "set: <$id>=$value"
        close_dataset $id $adlb::INTEGER $value
    }

    proc integer_get { id } {
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        incr i
        set result [ string range $s $i end ]
        debug "integer_get: <$id>=$result"
        return $result
    }

    proc float_init { id } {
        debug "float_init: <$id>"
        adlb::create $id "float"
        turbine::c::declare $id
    }

    proc float_set { id value } {
        debug "float_set: <$id>=$value"
        close_dataset $id $adlb::FLOAT $value
    }

    proc float_get { id } {
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        incr i
        set result [ string range $s $i end ]
        debug "float_get: <$id>=$result"
        return $result
    }

    proc string_init { id } {
        adlb::create $id "string"
        turbine::c::declare $id
    }

    proc string_set { id value } {
        debug "string_set: <$id>=$value"
        close_dataset $id $adlb::STRING $value
    }

    proc string_get { id } {
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        incr i
        set result [ string range $s $i end ]
        debug "string_get: <$id>=$result"
        return $result
    }

    proc container_init { id subscript_type } {
        adlb::create $id "container" $subscript_type
        turbine::c::declare $id
    }

    proc container_insert { id subscript value } {

        log "insert: <$id>\[$subscript\]=<$value>"
        adlb::insert $id $subscript $value
    }

    # Returns 0 if subscript is not found
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
        adlb::create $id "file" $path
        turbine::c::declare $id
    }

    proc file_set { id } {
        close_dataset $id $adlb::FILE none
    }

    proc filename { id } {
        debug "filename($id)"
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        set i [ expr $i + 1 ]
        set result [ string range $s $i end ]
        return $result
    }

    proc close_dataset { id type value } {
        global WORK_TYPE
        adlb::store $id $type $value
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
