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
        retrieve_string retrieve_decr_string          \
        create_integer store_integer                  \
        retrieve_integer retrieve_decr_integer        \
        create_float   store_float                    \
        retrieve_float retrieve_decr_float            \
        create_void    store_void                     \
        create_blob    store_blob                     \
        retrieve_blob retrieve_decr_blob              \
        create_ref     store_ref                      \
        retrieve_ref retrieve_decr_ref acquire_ref    \
        create_file_ref     store_file_ref            \
        retrieve_file_ref retrieve_decr_file_ref acquire_file_ref \
        create_struct     store_struct                \
        retrieve_struct retrieve_decr_struct acquire_struct \
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

    # usage: [<name>] <key_type> <val_type>
    proc allocate_container { args } {
        set length [ llength $args ]
        if { $length == 3  } {
            set name           [ lindex $args 0 ]
            set key_type [ lindex $args 1 ]
            set val_type [ lindex $args 2 ]
        } elseif { $length == 2 } {
            set name           ""
            set key_type [ lindex $args 0 ]
            set val_type [ lindex $args 1 ]
        } else {
            error "allocate_container: requires 2 or 3 args!"
        }
        set id [ create_container $::adlb::NULL_ID $key_type $val_type ]
        log "container: $name\[$key_type\]$val_type=<$id>"
        if { $name != "" } {
            upvar 1 $name v
            set v $id
        }
        return $id
    }

    # usage: <name> <subscript_type> [ args for create ]
    proc allocate_container_custom { name key_type val_type args } {
        set id [ create_container $::adlb::NULL_ID $key_type $val_type {*}${args} ]
        log "container: $name\[$key_type\]$val_type=<$id>"
        if { $name != "" } {
            upvar 1 $name v
            set v $id
        }
        return $id
    }

    # usage: <name> <mapping> [<create props>]
    # if unmapped, mapping should be set to the empty string
    # if mapped, mapping should be  turbine # string that will
    # at some point be set to a value.  This function expects that
    # a reference count be pre-incremented by two on the mapping string
    proc allocate_file2 { name filename {read_refcount 1} {write_refcount 1} args } {
        set is_mapped [ expr {! [ string equal $filename "" ]} ]
        # use void to signal file availability
        set signal [ allocate_custom "signal:$name" void \
                              $read_refcount $write_refcount {*}${args} ]
        if { $is_mapped } {
            log "file: $name=\[ <$signal> <$filename> \] mapped"

            # We got passed two references to the mapping string.
            # Adjust if this is off
            if { $read_refcount != 1 } {
                read_refcount_incr $filename [ expr $read_refcount - 1 ]
            }

        } else {
            # use new string that will be set later to something arbitrary
            # add an extra refcount for the closing rule below
            set filename [ allocate_custom "filename:$name" string \
                              [ expr $read_refcount + 1] {*}${args} ]
            log "file: $name=\[ <$signal> <$filename> \] unmapped"

        }
        # Filename may be read before assigning file.  To make sure
        # that filename isn't gc'ed before file is assigned, add a rule
        # to decrement the filename reference count once file status
        # is assigned
        rule $signal "read_refcount_decr $filename 1" \
            name "decr-filename-\[ $signal $filename \]"

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
        return [ adlb::create $id integer $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_integer { id value } {
        log "store: <$id>=$value"

        # Tcl cannot convert e.g., 099 to an integer.  Trim:
        if { ! [ string equal $value "0" ] } {
            set value [ string trimleft $value "0" ]
        }
        adlb::store $id integer $value
        c::cache_store $id integer $value
    }

    proc retrieve_integer { id {cachemode CACHED} {decrref 0} } {
        set cache [ string equal $cachemode CACHED ]
        if { $cache && [ c::cache_check $id ] } {
            set result [ c::cache_retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref integer ]
            } else {
              set result [ adlb::retrieve $id integer ]
            }

            if { $cache } {
              c::cache_store $id integer $result
            }
        }
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc retrieve_decr_integer { id {cachemode CACHED} } {
      return [ retrieve_integer $id $cachemode 1 ]
    }

    proc create_ref { id {read_refcount 1} {write_refcount 1} \
                             {permanent 0} } {
        return [ adlb::create $id ref $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_ref { id value } {
        log "store: <$id>=$value"
        adlb::store $id ref $value
        c::cache_store $id ref $value
    }

    proc retrieve_ref { id {cachemode CACHED} {decrref 0} } {
        set cache [ string equal $cachemode CACHED ]
        if { $cache && [ c::cache_check $id ] } {
            set result [ c::cache_retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref ref ]
            } else {
              set result [ adlb::retrieve $id ref ]
            }
            if { $cache } {
              c::cache_store $id ref $result
            }
        }
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc acquire_ref { id {incrref 1} {decrref 0} } {
        set result [ adlb::acquire_ref $id ref $incrref $decrref ]
        debug "acquire_ref: <$id>=$result"
        return $result
    }

    proc retrieve_decr_ref { id {cachemode CACHED} } {
      return [ retrieve_ref $id $cachemode 1 ]
    }

    proc create_file_ref { id {read_refcount 1} {write_refcount 1} \
                             {permanent 0} } {
        return [ adlb::create $id file_ref $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_file_ref { id value } {
        log "store: <$id>=$value"
        adlb::store $id file_ref $value
        c::cache_store $id file_ref $value
    }

    proc retrieve_file_ref { id {cachemode CACHED} {decrref 0} } {
        set cache [ string equal $cachemode CACHED ]
        if { $cache && [ c::cache_check $id ] } {
            set result [ c::cache_retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref file_ref ]
            } else {
              set result [ adlb::retrieve $id file_ref ]
            }

            if { $cache } {
              c::cache_store $id file_ref $result
            }
        }
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc retrieve_decr_file_ref { id {cachemode CACHED} } {
      return [ retrieve_file_ref $id $cachemode 1 ]
    }

    proc acquire_file_ref { id {incrref 1} {decrref 0} } {
        set result [ adlb::acquire_ref $id file_ref $incrref $decrref ]
        debug "acquire_file_ref: <$id>=$result"
        return $result
    }

    proc create_struct { id {read_refcount 1} {write_refcount 1} \
                             {permanent 0} } {
        return [ adlb::create $id struct $read_refcount \
                              $write_refcount $permanent ]
    }

    # store_struct <id> <value> <typename>
    # typename is a struct typename including struct subtype, e.g. "struct1"
    proc store_struct { id value typename } {
        log "store: <$id>=$value"
        adlb::store $id $typename $value
        c::cache_store $id $typename $value
    }

    proc retrieve_struct { id {cachemode CACHED} {decrref 0} } {
        set cache [ string equal $cachemode CACHED ]
        if { $cache && [ c::cache_check $id ] } {
            set result [ c::cache_retrieve $id ]
            if { $decrref } {
              read_structcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref struct ]
            } else {
              set result [ adlb::retrieve $id struct ]
            }
            
            if { $cache } {
              # TODO: need full struct type
              #c::cache_store $id struct $result
            }
        }
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc retrieve_decr_struct { id {cachemode CACHED} } {
      return [ retrieve_struct $id $cachemode 1 ]
    }

    proc acquire_struct { id {incrref 1} {decrref 0} } {
        set result [ adlb::acquire_ref $id struct $incrref $decrref ]
        debug "acquire_struct: <$id>=$result"
        return $result
    }

    proc acquire_subscript { id sub type {incrref 1} {decrref 0} } {
        set result [ adlb::acquire_sub_ref $id $sub $type \
                     $incrref $decrref ]
        debug "acquire_subscript: <$id>\[$sub\]=$result"
        return $result
    }


    proc create_float { id {read_refcount 1} {write_refcount 1} \
                           {permanent 0} } {
        return [ adlb::create $id float $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_float { id value } {
        log "store: <$id>=$value"
        adlb::store $id float $value
    }

    proc retrieve_float { id {cachemode CACHED} {decrref 0} } {
        set cache [ string equal $cachemode CACHED ]
        if { $cache && [ c::cache_check $id ] } {
            set result [ c::cache_retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref float ]
            } else {
              set result [ adlb::retrieve $id float ]
            }
            
            if { $cache } {
              c::cache_store $id float $result
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
        return [ adlb::create $id string $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_string { id value } {
        log "store: <$id>=[ log_string $value ]"
        adlb::store $id string $value
    }

    proc retrieve_string { id {cachemode CACHED} {decrref 0} } {
        set cache [ string equal $cachemode CACHED ]
        if { $cache && [ c::cache_check $id ] } {
            set result [ c::cache_retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref string ]
            } else {
              set result [ adlb::retrieve $id string ]
            }

            if { $cache } {
              c::cache_store $id string $result
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
        return [ adlb::create $id integer $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_void { id } {
        log "store: <$id>=void"
        # emulating void with integer
        adlb::store $id integer 12345
    }

    # retrieve_void not provided as it wouldn't do anything

    # Create blob
    proc create_blob { id {read_refcount 1} {write_refcount 1} \
                          {permanent 0} } {
        return [ adlb::create $id blob $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_blob { id value } {
      set ptr [ lindex $value 0 ]
      set len [ lindex $value 1 ]
      log [ format "store_blob: <%d>=\[pointer=%x length=%d\]" \
                                $id            $ptr      $len  ]
      adlb::store_blob $id $ptr $len
    }

    proc store_blob_string { id value } {
        log "store_blob_string: <$id>=[ log_string $value ]"
        set b [ adlb::blob_from_string $value ]
        adlb::store $id blob $b
        # Free memory
        adlb::local_blob_free $b
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
          set blob [ adlb::retrieve_decr $id $decrref blob ]
        } else {
          set blob [ adlb::retrieve $id blob ]
        }
        set result [ adlb::blob_to_string $blob ]
        adlb::local_blob_free $blob
        debug "retrieve_string: <$id>=[ log_string $result ]"
        return $result
    }

    proc retrieve_decr_blob_string { id } {
      return [ retrieve_blob_string $id 1 ]
    }
    
    proc multi_retrieve { ids {cachemode CACHED} args } {
      set result [ list ]
      foreach id $ids {
        if { [ string equal $cachemode CACHED ] &&
              [ c::cache_check $id ] } {
          set val [ c::cache_retrieve $id ]
        } else {
          set val [ adlb::retrieve $id {*}$args ]
        }
        lappend result $val
      }

      return $result
    }
    
    proc multi_retrieve_kv { ids {cachemode CACHED} args } {
      set result [ dict create ]

      dict for {key id} $ids {
        if { [ string equal $cachemode CACHED ] &&
              [ c::cache_check $id ] } {
          set val [ c::cache_retrieve $id ]
        } else {
          set val [ adlb::retrieve $id {*}$args ]
        }
        dict append result $key $val
      }

      return $result
    }

    proc multi_retrieve_decr { ids decr {cachemode CACHED} args } {
      set result [ list ]

      /oreach id $ids {
        if { [ string equal $cachemode CACHED ] &&
              [ c::cache_check $id ] } {
          set val [ c::cache_retrieve $id ]
          read_refcount_decr $id $decr
        }
        else {
          set val [ adlb::retrieve_decr $id $decr {*}$args ]
        }
        lappend result $val
      }

      return $result
    }
    
    proc multi_retrieve_kv_decr { ids decr {cachemode CACHED} args } {
      set result [ dict create ]

      dict for {key id} $ids {
        if { [ string equal $cachemode CACHED ] &&
              [ c::cache_check $id ] } {
          set val [ c::cache_retrieve $id ]
          read_refcount_decr $id $decr
        }
        else {
          set val [ adlb::retrieve_decr $id $decr {*}$args ]
        }
        dict append result $key $val
      }

      return $result
    }

    proc create_container { id key_type val_type {read_refcount 1} \
                          {write_refcount 1} {permanent 0}} {
        return [ adlb::create $id container $key_type $val_type \
                            $read_refcount $write_refcount $permanent]
    }

    # usage: container_insert <id> <subscript> <member> <type> [<drops>]
    # @param drops = 0 by default
    proc container_insert { id subscript member type {drops 0} } {
        log "insert: <$id>\[\"$subscript\"\]=<$member>"
        adlb::insert $id $subscript $member $type $drops
    }

    # Returns 0 if subscript is not found
    proc container_lookup { id subscript {read_decr 0} } {
        set s [ adlb::lookup $id $subscript $read_decr ]
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

    proc filename { id } {
        debug "filename($id)"
        set s [ adlb::retrieve $id ]
        set i [ string first : $s ]
        set i [ expr {$i + 1} ]
        set result [ string range $s $i end ]
        return $result
    }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
