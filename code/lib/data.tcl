# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# Turbine data abstraction layer

namespace eval turbine {

    namespace import ::adlb::read_refcount_*
    namespace export                                  \
        allocate retrieve                             \
        create_string  store_string                   \
        create_integer store_integer                  \
        retrieve_integer retrieve_decr_integer        \
        create_float   store_float                    \
        retrieve_float retrieve_decr_float            \
        create_void    store_void                     \
        create_file    store_file                     \
        create_blob    store_blob                     \
        retrieve_blob retrieve_decr_blob              \
        retrieve_decr_blob_string                     \
        allocate_container                            \
        container_lookup container_list               \
        container_insert notify_waiter                \
        read_refcount_incr read_refcount_decr         \
        allocate_file2 filename

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
                set t "\"$t\""
                if { [ string length $s ] > $log_string_mode } {
                    set t "${t}..."
                }
                return $t
            }
        }
    }

    # usage: allocate [<name>] <type>
    # If name is given, print a log message
    proc allocate { args } {
        set length [ llength $args ]
        if { $length == 2 } {
            set name       [ lindex $args 0 ]
            set type       [ lindex $args 1 ]
        } elseif { $length == 1 } {
            set name       ""
            set type       [ lindex $args 0 ]
        } else {
            error "allocate: requires 1 or 2 args!"
        }

        set id [ create_$type $::adlb::NULL_ID ]
        if { $name == "" } {
            log "allocated $type: <$id>"
        } else {
            log "allocated $type: $name=<$id>"
            upvar 1 $name v
            set v $id
        }
        return $id
    }

    # usage: allocate_custom <name> <type> [ args to pass to create ]
    proc allocate_custom { name type args } {
        set id [ create_$type $::adlb::NULL_ID {*}${args} ]

        if { $name == "" } {
            log "allocated $type: <$id>"
        } else {
            log "allocated $type: $name=<$id>"
            upvar 1 $name v
            set v $id
        }
        return $id
    }

    # usage: [<name>] <subscript_type>
    proc allocate_container { args } {
        set length [ llength $args ]
        if { $length == 2  } {
            set name           [ lindex $args 0 ]
            set subscript_type [ lindex $args 1 ]
        } elseif { $length == 1 } {
            set name           ""
            set subscript_type $args
        } else {
            error "allocate_container: requires 1 or 2 args!"
        }
        set id [ create_container $::adlb::NULL_ID $subscript_type ]
        log "container: $name\[$subscript_type\]=<$id>"
        if { $name != "" } {
            upvar 1 $name v
            set v $id
        }
        return $id
    }

    # usage: <name> <subscript_type> [ args for create ]
    proc allocate_container_custom { name subscript_type args } {
        set id [ create_container $::adlb::NULL_ID $subscript_type {*}${args} ]
        log "container: $name\[$subscript_type\]=<$id>"
        if { $name != "" } {
            upvar 1 $name v
            set v $id
        }
        return $id
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

    # usage: <name> <mapping> [<create props>]
    # if unmapped, mapping should be set to the empty string
    # if mapped, mapping should be  turbine # string that will
    # at some point be set to a value
    proc allocate_file2 { name filename {read_refcount 1} args } {
        set is_mapped [ expr {! [ string equal $filename "" ]} ]
        # use void to signal file availability
        set signal [ allocate_custom "signal:$name" void \
                              $read_refcount {*}${args} ]
        if { $is_mapped } {
            read_refcount_incr $filename $read_refcount
            log "file: $name=\[ <$signal> <$filename> \] mapped"
        } else {
            # use new string that will be set later to
            # something arbitrary
            set filename [ allocate_custom "filename:$name" string \
                              $read_refcount {*}${args} ]
            log "file: $name=\[ <$signal> <$filename> \] unmapped"

        }
        set u [ list $signal $filename $is_mapped ]
        upvar 1 $name v
        set v $u
        return $u
    }

    # usage: retrieve <id>
    # Not type checked
    # Always stores result as Tcl string
    proc retrieve { id {decrref 0} } {
        if { $decrref } {
          set result [ adlb::retrieve_decr $id $decrref ]
        } else {
          set result [ adlb::retrieve $id ]
        }
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc retrieve_decr { id } {
        return [ retrieve $id 1 ]
    }

    proc create_integer { id {read_refcount 1} {write_refcount 1} \
                             {permanent 0} } {
        return [ adlb::create $id $::adlb::INTEGER $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_integer { id value } {
        log "store: <$id>=$value"

        # Tcl cannot convert e.g., 099 to an integer.  Trim:
        if { ! [ string equal $value "0" ] } {
            set value [ string trimleft $value "0" ]
        }
        set waiters [ adlb::store $id $::adlb::INTEGER $value ]
        notify_waiters $id $waiters
        c::cache store $id $::adlb::INTEGER $value
    }

    proc retrieve_integer { id {cachemode CACHED} {decrref 0} } {
        if { [ string equal $cachemode CACHED ] && [ c::cache check $id ] } {
            set result [ c::cache retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref $::adlb::INTEGER ]
            } else {
              set result [ adlb::retrieve $id $::adlb::INTEGER ]
            }
        }
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc retrieve_decr_integer { id {cachemode CACHED} } {
      return [ retrieve_integer $id $cachemode 1 ]
    }

    proc create_float { id {read_refcount 1} {write_refcount 1} \
                           {permanent 0} } {
        return [ adlb::create $id $::adlb::FLOAT $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_float { id value } {
        log "store: <$id>=$value"
        set waiters [ adlb::store $id $::adlb::FLOAT $value ]
        notify_waiters $id $waiters
    }

    proc retrieve_float { id {cachemode CACHED} {decrref 0} } {
        if { [ string equal $cachemode CACHED ] && [ c::cache check $id ] } {
            set result [ c::cache retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref $::adlb::FLOAT ]
            } else {
              set result [ adlb::retrieve $id $::adlb::FLOAT ]
            }
        }
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc retrieve_decr_float { id {cachemode CACHED} } {
      return [ retrieve_float $id $cachemode 1 ]
    }

    proc create_string { id {read_refcount 1} {write_refcount 1} \
                            {permanent 0} } {
        return [ adlb::create $id $::adlb::STRING $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_string { id value } {
        log "store: <$id>=[ log_string $value ]"
        set waiters [ adlb::store $id $::adlb::STRING $value ]
        notify_waiters $id $waiters
    }

    proc retrieve_string { id {cachemode CACHED} {decrref 0} } {
        if { [ string equal $cachemode CACHED ] && [ c::cache check $id ] } {
            set result [ c::cache retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref $::adlb::STRING ]
            } else {
              set result [ adlb::retrieve $id $::adlb::STRING ]
            }
        }
        debug "retrieve: <$id>=[ log_string $result ]"
        return $result
    }

    proc retrieve_decr_string { id {cachemode CACHED} } {
      return [ retrieve_string $id $cachemode 1 ]
    }

    proc create_void { id {read_refcount 1} {write_refcount 1} \
                          {permanent 0} } {
        # emulating void with integer
        return [ adlb::create $id $::adlb::INTEGER $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_void { id } {
        log "store: <$id>=void"
        # emulating void with integer
        set waiters [ adlb::store $id $::adlb::INTEGER 12345 ]
        notify_waiters $id $waiters
    }

    # retrieve_void not provided as it wouldn't do anything

    # Create blob
    proc create_blob { id {read_refcount 1} {write_refcount 1} \
                          {permanent 0} } {
        return [ adlb::create $id $::adlb::BLOB $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_blob { id value } {
      set ptr [ lindex $value 0 ]
      set len [ lindex $value 1 ]
      log [ format "store_blob: <%d>=\[pointer=%x length=%d\]" \
                                $id            $ptr      $len  ]
      set waiters [ adlb::store_blob $id $ptr $len ]
      notify_waiters $id $waiters
    }

    proc store_blob_string { id value } {
        log "store_blob_string: <$id>=[ log_string $value ]"
        set waiters [ adlb::store $id $::adlb::BLOB $value ]
        notify_waiters $id $waiters
    }

    # Retrieve and cache blob
    proc retrieve_blob { id {decrref 0} } {
      if { $decrref } {
        set result [ adlb::retrieve_decr_blob $id $decrref ]
      } else {
        set result [ adlb::retrieve_blob $id ]
      }
      log [ format "retrieve_blob: <%d>=\[%x %d\]" $id \
                    [ lindex $result 0 ] [ lindex $result 1 ] ]
      return $result
    }

    proc retrieve_decr_blob { id {decrref 1} } {
      return [ retrieve_blob $id $decrref ]
    }
    # release reference to cached blob
    proc free_blob { id } {
      debug "free_blob: <$id>"
      adlb::blob_free $id
    }

    # Free local blob
    proc free_local_blob { blob } {
      if { [ llength $blob ] == 3 } {
        debug [ format "free_local_blob: \[%x %d %d\]" \
                    [ lindex $blob 0 ] [ lindex $blob 1 ] \
                    [ lindex $blob 2 ] ]
      } else {
        debug [ format "free_local_blob: \[%x %d\]" \
                    [ lindex $blob 0 ] [ lindex $blob 1 ] ]
      }
      adlb::local_blob_free $blob
    }

    proc retrieve_blob_string { id {decrref 0} } {
        if { $decrref } {
          set result [ adlb::retrieve_decr $id $decrref $::adlb::BLOB ]
        } else {
          set result [ adlb::retrieve $id $::adlb::BLOB ]
        }
        debug "retrieve_string: <$id>=[ log_string $result ]"
        return $result
    }

    proc retrieve_decr_blob_string { id } {
      return [ retrieve_blob_string $id 1 ]
    }

    proc create_container { id subscript_type {read_refcount 1} \
                          {write_refcount 1} {permanent 0}} {
        return [ adlb::create $id $::adlb::CONTAINER $subscript_type \
                            $read_refcount $write_refcount $permanent]
    }

    # usage: container_insert <id> <subscript> <member> [<drops>]
    # @param drops = 0 by default
    proc container_insert { id subscript member {drops 0} } {
        log "insert: <$id>\[$subscript\]=<$member>"
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
        return [ adlb::create $id $::adlb::FILE $path ]
    }

    proc store_file { id } {
        set waiters [ adlb::store $id $::adlb::FILE "" ]
        notify_waiters $id $waiters
    }

    proc filename { id } {
        debug "filename($id)"
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        set i [ expr {$i + 1} ]
        set result [ string range $s $i end ]
        return $result
    }

    proc notify_waiters { id ranks } {
        global WORK_TYPE
        foreach rank $ranks {
            debug "notify: $rank"
            adlb::put $rank $WORK_TYPE(CONTROL) "close $id" \
                        [ get_priority ] 1
        }
    }
}
