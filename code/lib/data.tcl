
# Turbine data abstraction layer

namespace eval turbine {

    namespace export                          \
        allocate get                          \
        create_string  set_string             \
        create_integer set_integer            \
        create_float   set_float              \
        create_file    set_file               \
        allocate_container                    \
        container_get container_list          \
        container_insert close_container      \
        file_set filename

    # namespace import adlb::INTEGER adlb::STRING

#     proc typeof { id } {
#         set s [ adlb::retrieve $id ]
#         set i [ string first : $s ]
#         incr i -1
#         set result [ string range $s 0 $i ]
#         return $result
#     }

    # usage: allocate [<name>] [<type>]
    # If name is given, print a log message
    proc allocate { args } {
        set u [ adlb::unique ]
        set length [ llength $args ]
        puts "len: $length"
        if { $length == 2 } {
            set name [ lindex $args 0 ]
            set type [ lindex $args 1 ]
            log "${type}: $name=<$u>"
            upvar 1 $name v
            set v $u
        } elseif { $length == 1 } {
            set type $args
        } else {
            error "allocate: requires 1 or 2 args!"
        }
        create_$type $u
        return $u
    }

    # Obtain unique TD
    # If given an argument, sets the variable by that name
    # and a log message is reported
    # proc allocate_integer { args } {
    #     set u [ adlb::unique ]
    #     if { [ string length $args ] } {
    #         log "integer: $args=<$u>"
    #         upvar 1 $args v
    #         set v $u
    #     }
    #     create_integer $u
    #     return $u
    # }

    # usage: [<name>] <subscript_type>
    proc allocate_container { args } {
        set u [ adlb::unique ]
        set length [ llength $args ]
        if { $length == 2  } {
            set name           [ lindex $args 0 ]
            set subscript_type [ lindex $args 1 ]
            log "container: $name\[$subscript_type\]=<$u>"
            upvar 1 $name v
            set v $u
        } elseif { $length == 1 } {
            set subscript_type $args
        } else {
            error "allocate_container: requires 1 or 2 args!"
        }
        create_container $u $subscript_type
        return $u
    }

    # proc create { type id } {
    #     debug "create $type: <$id>"
    #     adlb::create $id $type
    # }

    # usage: get <id>
    proc get { id } {
        set result [ adlb::retrieve $id ]
        debug "get: <$id>=$result"
        return $result
    }

    proc create_integer { id } {
        debug "create_integer: <$id>"
        adlb::create $id $adlb::INTEGER
    }

    proc set_integer { id value } {
        log "set: <$id>=$value"
        close_dataset $id $adlb::INTEGER $value
    }

    proc get_integer { id } {
        set result [ adlb::retrieve $id $adlb::INTEGER ]
        debug "get_integer: <$id>=$result"
        return $result
    }

    # proc get_integer { id } {
    #     set s [ adlb::retrieve $id ]
    #     set i [ string first : $s ]
    #     incr i
    #     set result [ string range $s $i end ]
    #     debug "get_integer: <$id>=$result"
    #     return $result
    # }

    proc create_float { id } {
        debug "create_float: <$id>"
        adlb::create $id $adlb::FLOAT
    }

    proc set_float { id value } {
        debug "set: <$id>=$value"
        close_dataset $id $adlb::FLOAT $value
    }

    proc get_float { id } {
        set result [ adlb::retrieve $id $adlb::FLOAT ]
        debug "get_float: <$id>=$result"
        return $result
    }

    proc create_string { id } {
        debug "create_string: <$id>"
        adlb::create $id $adlb::STRING
    }

    proc set_string { id value } {
        debug "set: <$id>=$value"
        close_dataset $id $adlb::STRING $value
    }

    proc get_string { id } {
        set result [ adlb::retrieve $id $adlb::STRING ]
        debug "get_float: <$id>=$result"
        return $result
    }

    proc create_container { id subscript_type } {
        debug "create_container: <$id>\[$subscript_type\]"
        adlb::create $id $adlb::CONTAINER $subscript_type
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

    proc create_file { id path } {
        adlb::create $id $adlb::FILE $path
    }

    proc set_file { id } {
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
