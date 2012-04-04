
# Turbine data abstraction layer

namespace eval turbine {

    namespace export                           \
        allocate get                           \
        create_string  set_string              \
        create_integer set_integer get_integer \
        create_float   set_float   get_float   \
        create_void    set_void                \
        create_file    set_file                \
        create_blob                            \
        get_blob_string                        \
        allocate_container                     \
        container_get container_list           \
        container_insert close_datum       \
        file_set filename

    # usage: allocate [<name>] [<type>]
    # If name is given, print a log message
    proc allocate { args } {
        set u [ adlb::unique ]
        set length [ llength $args ]
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

    # usage: [<name>] [<mapping>]
    proc allocate_file { args } {
        set u [ adlb::unique ]
        set length [ llength $args ]
        if { $length == 2 } {
            set name [ lindex $args 0 ]
            set mapping [ lindex $args 1 ]
            log "file: $name=<$u> mapped to '${mapping}'"
            upvar 1 $name v
            set v $u
        } elseif { $length == 1 } {
            set mapping $args
        } else {
            error "allocate: requires 1 or 2 args!"
        }
        create_file $u $mapping
        return $u
    }

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
        adlb::store $id $adlb::INTEGER $value
        close_datum $id
    }

    proc get_integer { id } {
        set result [ adlb::retrieve $id $adlb::INTEGER ]
        debug "get_integer: <$id>=$result"
        return $result
    }

    proc create_float { id } {
        debug "create_float: <$id>"
        adlb::create $id $adlb::FLOAT
    }

    proc set_float { id value } {
        log "set: <$id>=$value"
        adlb::store $id $adlb::FLOAT $value
        close_datum $id
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
        log "set: <$id>=\"$value\""
        adlb::store $id $adlb::STRING $value
        close_datum $id
    }

    proc get_string { id } {
        set result [ adlb::retrieve $id $adlb::STRING ]
        debug "get_string: <$id>=$result"
        return $result
    }

    proc create_void { id } {
        debug "create_void: <$id>"
        # TODO: for now emulate void with integer
        adlb::create $id $adlb::INTEGER
    }

    proc set_void { id } {
        debug "set_void: <$id>"
        # TODO: for now emulate void with integer
        adlb::store $id $adlb::INTEGER 12345
        close_datum $id
    }

    # get_void not provided as it wouldn't do anything

    # Create blob
    proc create_blob { id } {
        adlb::create $id $adlb::BLOB
    }

    proc set_blob_string { id value } {
        log "set_blob: <$id>=$value"
        adlb::store $id $adlb::BLOB $value
        close_datum $id
    }

    proc get_blob_string { id } {
        set result [ adlb::retrieve $id $adlb::BLOB ]
        debug "get_string: <$id>=$result"
        return $result
    }

    proc create_container { id subscript_type } {
        log "create_container: <$id>\[$subscript_type\]"
        adlb::create $id $adlb::CONTAINER $subscript_type
    }

    # usage: container_insert <id> <subscript> <member> [<drops>]
    # @param drops = 0 by default
    proc container_insert { args } {

        set id        [ lindex $args 0 ]
        set subscript [ lindex $args 1 ]
        set member    [ lindex $args 2 ]
        set drops 0
        if { [ llength $args ] == 4 } {
            set drops [ lindex $args 3 ]
        }
        log "insert: <$id>\[$subscript\]=<$member>"
        adlb::insert $id $subscript $member $drops
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
        set result [ adlb::enumerate $id subscripts all 0 ]
        return $result
    }

    proc create_file { id path } {
        adlb::create $id $adlb::FILE $path
    }

    proc set_file { id } {
        close_datum $id
    }

    proc filename { id } {
        debug "filename($id)"
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        set i [ expr $i + 1 ]
        set result [ string range $s $i end ]
        return $result
    }

    proc close_datum { id } {
        global WORK_TYPE
        set ranks [ adlb::close $id ]
        foreach rank $ranks {
            debug "notify: $rank"
            adlb::put $rank $WORK_TYPE(CONTROL) "close $id"
        }
    }
}
