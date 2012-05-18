
# Turbine data abstraction layer

namespace eval turbine {

    namespace export                                  \
        allocate retrieve                             \
        create_string  store_string                   \
        create_integer store_integer retrieve_integer \
        create_float   store_float   retrieve_float   \
        create_void    store_void                     \
        create_file    store_file                     \
        create_blob                                   \
        retrieve_blob_string                          \
        allocate_container                            \
        container_lookup container_list               \
        container_insert close_datum                  \
        filename

    # Shorten strings in the log if the user requested that
    # log_string_mode is set at init time
    #                 and is either ON, OFF, or a length
    proc log_string { s } {

        variable log_string_mode
        switch $log_string_mode {
            ON  { return "\"$s\"" }
            OFF { return "" }
            default {
                set t [ string range $s 0 $log_string_mode ]
                return "\"$t\"..."
            }
        }
    }

    # usage: allocate [<name>] <type>
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

    # usage: retrieve <id>
    # Not type checked
    # Always tores result as Tcl string
    proc retrieve { id } {
        set result [ adlb::retrieve $id ]
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc create_integer { id } {
        debug "create integer: <$id>"
        adlb::create $id $adlb::INTEGER
    }

    proc store_integer { id value } {
        log "store: <$id>=$value"
        adlb::store $id $adlb::INTEGER $value
        close_datum $id
    }

    proc retrieve_integer { id } {
        set result [ adlb::retrieve $id $adlb::INTEGER ]
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc create_float { id } {
        debug "create float: <$id>"
        adlb::create $id $adlb::FLOAT
    }

    proc store_float { id value } {
        log "store: <$id>=$value"
        adlb::store $id $adlb::FLOAT $value
        close_datum $id
    }

    proc retrieve_float { id } {
        set result [ adlb::retrieve $id $adlb::FLOAT ]
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc create_string { id } {
        debug "create string: <$id>"
        adlb::create $id $adlb::STRING
    }

    proc store_string { id value } {
        log "store: <$id>=[ log_string $value ]"
        adlb::store $id $adlb::STRING $value
        close_datum $id
    }

    proc retrieve_string { id } {
        set result [ adlb::retrieve $id $adlb::STRING ]
        debug "retrieve: <$id>=[ log_string $result ]"
        return $result
    }

    proc create_void { id } {
        debug "create void: <$id>"
        # TODO: for now emulate void with integer
        adlb::create $id $adlb::INTEGER
    }

    proc store_void { id } {
        debug "store void: <$id>"
        # TODO: for now emulate void with integer
        adlb::store $id $adlb::INTEGER 12345
        close_datum $id
    }

    # retrieve_void not provided as it wouldn't do anything

    # Create blob
    proc create_blob { id } {
        log "create blob: <$id>"
        adlb::create $id $adlb::BLOB
    }

    proc store_blob_string { id value } {
        log "store_blob: <$id>=[ log_string $value ]"
        adlb::store $id $adlb::BLOB $value
        close_datum $id
    }

    proc retrieve_blob_string { id } {
        set result [ adlb::retrieve $id $adlb::BLOB ]
        debug "retrieve_string: <$id>=[ log_string $result ]"
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
        log "insert: <$id>\[\"$subscript\"\]=<$member>"
        adlb::insert $id $subscript $member $drops
    }

    # Returns 0 if subscript is not found
    proc container_lookup { id subscript } {
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

    proc store_file { id } {
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
            adlb::put $rank $WORK_TYPE(CONTROL) "close $id" \
                      $turbine::priority
        }
    }
}
