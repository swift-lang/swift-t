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

# Turbine builtin container operations

# Rule debug token conventions:
#  1) Use shorthand notation from Turbine Internals Guide
#  2) Preferably, just specify output TDs.  Input TDs are reported by
#     the WAITING TRANSFORMS list and the rule debugging lines

namespace eval turbine {
    namespace export container_f_get container_f_insert
    namespace export c_f_lookup deeprule
    namespace export swift_array_build

    namespace import ::adlb::create_nested* \
                     ::adlb::struct_create_nested*

    # build integer keyed array by inserting items into a container
    # starting at 0
    # write_decr: decrement writers count
    # args: type of array values, passed to adlb::store
    proc array_build { c vals write_decr args } {
      set kv_dict [ dict create ]
      set i 0
      foreach val $vals {
        dict append kv_dict $i $val
        incr i
      }
      array_kv_build $c $kv_dict $write_decr integer {*}$args
    }

    proc swift_array_build { elems var_type } {
        set n [ llength $elems ]
        log "swift_array_build: elems: $n var_type: $var_type"

        set result [ dict create ]
        if { $var_type == "file" } {
            # set type "file_ref"
            for { set i 0 } { $i < $n } { incr i } {
                set elem [ lindex $elems $i ]
                dict append result $i [ create_local_file_ref $elem 1 ]
            }
        } else {
            for { set i 0 } { $i < $n } { incr i } {
                set elem [ lindex $elems $i ]
                dict append result $i $elem
            }
        }
        return $result
    }

    # build array by inserting items into a container starting at 0
    # write_decr: decrement writers count
    # key_type: array key type
    # args: type of array values, passed to adlb::store
    proc array_kv_build { c kv_dict write_decr key_type args } {
      log "array_kv_build: <$c> [ dict size $kv_dict ] elems, write_decr $write_decr"
      adlb::store $c container $key_type {*}$args $kv_dict $write_decr
    }

    # build array from values
    # write_decr: decrement writers count
    # key_type: array key type
    # args: type of array values, passed to adlb::store
    proc array_kv_build2 { c kv_dict write_decr key_type args } {
      set n [ dict size $kv_dict ]
      set typel $args
      # Add decr to list
      lappend typel 1 1

      if { $n > 0 } {
        set elems [ adlb::multicreate {*}[ lrepeat $n $typel ] ]
      } else {
        # Avoid lrepeat not support 0 repeats in tcl < 8.6
        set elems [ list ]
      }
      log "array_kv_build2: <$c> [ dict size $kv_dict ] elems, write_decr $write_decr"
      set kv_dict2 [ dict create ]
      set i 0
      dict for { key val } $kv_dict {
        set elem [ lindex $elems $i ]
        adlb::store $elem $val_type $val
        dict append kv_dict2 $key $elem
        incr i
      }
      array_kv_build $c $kv_dict2 $write_decr $key_type {*}$args
    }


    # build multiset by inserting items into a container starting at 0
    # write_decr: decrement writers count
    # args: type of multiset elems, passed to adlb::store
    proc multiset_build { ms elems write_decr args } {
      set n [ llength $elems ]
      log "multiset_build: <$ms> $n elems, write_decr $write_decr"
      adlb::store $ms multiset {*}$args $elems $write_decr
    }

    proc type_create_slice { type_list pos } {
      set outer_type [ lindex $type_list $pos ]
      switch $outer_type {
        container {
          # Include key and value types
          return [ lrange $type_list $pos [ expr {$pos + 2} ] ]
        }
        multiset {
          # Include value type
          return [ lrange $type_list $pos [ expr {$pos + 1} ] ]
        }
        default {
          return [ list $outer_type ]
        }
      }
    }

    # Recursively build a nested ADLB structure with containers/multisets/etc
    # types: list of types from outer to inner.
    #        key/value types are both included in list
    # types_pos: current position in types list
    proc build_rec { id cval types {types_pos 0} {write_decr 1}} {
      log "build_rec: <$id>"
      set outer_type [ lindex $types $types_pos ]

      # If there are more than two entries left in the type list
      # (the leaf type, and another container type), we will
      # recurse to handle that.

      switch $outer_type {
        container {
          set n [ dict size $cval ]
          set key_type_pos [ expr {$types_pos + 1} ]
          set key_type [ lindex $types $key_type_pos ]
          set val_type_pos [ expr {$types_pos + 2} ]
          set val_type [ lindex $types $val_type_pos ]

          switch $val_type {
            ref -
            file_ref {
              # Refs must be handled by creating inner TDs
              # appropriate slice of types depending on type
              set create_types [ type_create_slice $types \
                                      [ expr {$val_type_pos + 1} ] ]
              # initial refcounts
              lappend create_types 1 1
              if { $n > 0 } {
                set val_ids [ adlb::multicreate {*}[ lrepeat $n \
                                                      $create_types ] ]
              } else {
                # Avoid lrepeat not support 0 repeats in tcl < 8.6
                set val_ids [ list ]
              }
              set val_dict [ dict create ]

              set i 0
              dict for { key val } $cval {
                set val_id [ lindex $val_ids $i ]
                if { $val_type == "file_ref" } {
                  set val_id [ file_handle_from_td $val_id 0 ]
                  lset val_ids $i $val_id
                }

                # build inner data structure
                build_rec $val_id $val $types [ expr {$val_type_pos + 1 } ]

                dict append val_dict $key $val_id
                incr i
              }
            }
            default {
              # Not a ref: data stored directly in dict
              set val_dict $cval
            }
          }
          # Store values all at once
          adlb::store $id container $key_type $val_type $val_dict $write_decr
        }
        multiset {
          set n [ llength $cval ]
          set val_type_pos [ expr {$types_pos + 1} ]
          set val_type [ lindex $types $val_type_pos ]
          switch $val_type {
            ref -
            file_ref {
              # Refs must be handled by creating inner TDs
              # appropriate slice of types depending on type
              set create_types [ type_create_slice $types \
                                      [ expr {$val_type_pos + 1} ] ]
              # initial refcounts
              lappend create_types 1 1
              if { $n > 0 } {
                set val_list [ adlb::multicreate {*}[ lrepeat $n $create_types ] ]
              } else {
                # Avoid lrepeat not support 0 repeats in tcl < 8.6
                set val_list [ list ]
              }

              set i 0
              foreach val $cval {
                set val_id [ lindex $val_list $i ]
                if { $val_type == "file_ref" } {
                  set val_id [ file_handle_from_td $val_id 0 ]
                  lset val_ids $i $val_id
                }

                # build inner data structure
                build_rec $val_id $val $types [ expr {$val_type_pos + 1 } ]

                incr i
              }
            }
            default {
              set val_list $cval
            }
          }
          # Store values all at once
          adlb::store $id multiset $val_type $val_list $write_decr
        }
        file {
          store_file $id cval
        }
        default {
          if { [ adlb::is_struct_type $outer_type ] && \
               [ llength $types ] > [ expr {$types_pos + 1} ] } {
            set field_types [ lindex $types [ expr {$types_pos + 1} ] ]
            build_struct_rec $id $cval $outer_type $field_types $write_decr
          } elseif [ expr {$types_pos + 1 == [ llength $types ]} ] {
            # Don't need to recurse: just store
            adlb::store $id $outer_type $cval
          } else {
            error "Expected type to recursively enumerate: $outer_type"
          }
        }
      }
    }

    # Build a struct recursively with inner references
    # val: Tcl data structure with data
    # struct_type: specific struct type
    # field_types: dict mapping field name to field type info
    proc build_struct_rec { id val struct_type field_types {write_decr 1} } {
      set store_val [ build_struct_val_rec $val $field_types ]

      adlb::store $id $struct_type $store_val $write_decr
    }

    proc build_struct_val_rec { val field_types } {
      set store_val [ dict create ]

      dict for { key field_type } $field_types {
        set field_val [ dict get $val $key ]
        set field_outer_type [ lindex $field_type 0 ]

        switch $field_outer_type {
          ref -
          file_ref {
            set create_type [ type_create_slice $field_type 1 ]
            set val_id [ adlb::create $adlb::NULL_ID {*}$create_type ]
            if { $field_type == "file_ref" } {
              set val_id [ file_handle_from_td $val_id 0 ]
            }

            # build inner data structure
            build_rec $val_id $field_val $field_type 1
            set store_field_val $val_id
          }
          default {
            if { [ adlb::is_struct_type $field_outer_type ] && \
                 [ llength $field_type ] > 1 } {
              # Use additional type info to build struct recursively
              set field_struct_fields [ lindex $field_type 1 ]
              set store_field_val [ build_struct_val_rec $field_val \
                                                $field_struct_fields ]
            } else {
              # Store unmodified
              set store_field_val $field_val
            }
          }
        }

        dict append store_val $key $store_field_val
      }

      return $store_val
    }

    # Just like adlb::container_reference but add logging
    # Note that container_reference always consumes a read reference count
    proc container_reference { c i r type } {
        log "creating reference: <$c>\[\"$i\"\] <- <*$r> ($type)"
        adlb::container_reference $c $i $r $type
    }

    # Same as container_lookup, but fail if item does not exist
    # deprecated: container_lookup now checks
    proc container_lookup_checked { c i } {
        return [ container_lookup $c $i ]
    }

    # CFRI
    # When i is closed, set d := c[i] (by value copy)
    # d: the destination, an integer
    # inputs:
    # c: the container
    # i: the subscript (any type)
    proc c_f_retrieve_integer { d c i } {
        rule $i "c_f_retrieve_integer_body $d $c $i" \
            name "CFRI-$c-$i"
    }

    proc c_f_retrieve_integer_body { d c i } {
        set s [ retrieve_decr $i ]
        set t [ container_lookup $c $s ]
        if { $t == 0 } {
            error "lookup failed: c_f_retrieve <$c>\[$s\]"
        }
        set value [ retrieve_integer $t ]
        store_integer $d $value
    }

    # CFI
    # When i is closed, set c[i] := d (by insertion)
    # inputs:
    # c: the container
    # i: the subscript (any type)
    # d: the data
    # t: the type
    # outputs: ignored.  To block on this, use turbine::reference
    # Note: assume slot kept open by other process
    proc c_f_insert { c i d t {write_refcount_decrs 1} {write_refcount_incr 1}} {
        nonempty c i t d

        if { $write_refcount_incr } {
            write_refcount_incr $c
        }

        rule $i [ list turbine::c_f_insert_body $c $i $d $t $write_refcount_decrs ] \
            name "CFI-$c-$i"
    }
    proc c_f_insert_body { c i d t write_refcount_decrs } {
        set s [ retrieve_decr $i ]
        container_insert $c $s $d $t $write_refcount_decrs
    }

    # CFIR
    # When i and r are closed, set c[i] := *(r)
    # inputs: c i r t
    # r: a reference to a turbine ID
    proc c_f_insert_r { c i r t {write_refcount_decrs 1} {write_refcount_incr 1}} {
        nonempty c i r t

        if { $write_refcount_incr } {
            write_refcount_incr $c
        }

        rule "$i $r" \
            "c_f_insert_r_body $c $i $r $t $write_refcount_decrs" \
            name "CFIR-$c-$i"
    }

    proc c_f_insert_r_body { c i r t write_refcount_decrs } {
        set t1 [ retrieve_decr $i ]
        set d [ adlb::acquire_ref $r $t 1 1 ]
        container_insert $c $t1 $d $t $write_refcount_decrs
    }

    # CVIR
    # When r is closed, set c[i] := *(r)
    # inputs: c i r t
    # i: an integer which is the index to insert into
    # r: a reference to a turbine ID
    proc c_v_insert_r { c i r t {write_refcount_decrs 1} {write_refcount_incr 1}} {
        nonempty c i r t

        if { $write_refcount_incr } {
            write_refcount_incr $c
        }

        rule $r "c_v_insert_r_body $c $i $r $t $write_refcount_decrs" \
            name "container_deref_insert-$c-$i"
    }

    proc c_v_insert_r_body { c i r t write_refcount_decrs } {
        set d [ adlb::acquire_ref $r $t 1 1 ]
        # Refcount from reference passed to container
        container_insert $c $i $d $t $write_refcount_decrs
    }

    # Immediately insert data into container without affecting open slot count
    # c: the container
    # i: the subscript
    # d: the data
    # outputs: ignored.
    proc container_immediate_insert { c i d t {drops 0} } {
        # write_refcount_incr $c
        container_insert $c $i $d $t $drops
    }

    # CFL
    # When i is closed, get a reference on c[i] in TD r
    # Thus, you can block on r and be notified when c[i] exists
    # r is an integer.  The value of r is the TD of c[i]
    # inputs: c i r adlb_type
    # outputs: None.  You can block on d with turbine::dereference
    # c: the container
    # i: the subscript (any type)
    # r: the reference TD
    # ref_type: internal representation type for reference
    proc c_f_lookup { c i r ref_type } {
        debug "CFL: <$c>\[<$i>\] <- <*$r>"

        rule $i "c_f_lookup_body $c $i $r $ref_type" \
            name "CFL-$c-$i"
    }
    proc c_f_lookup_body { c i r ref_type } {
        debug "f_reference_body: <$c>\[<$i>\] <- <*$r>"
        set t1 [ retrieve_decr $i ]
        debug "f_reference_body: <$c>\[$t1\] <- <$r>"
        container_reference $c $t1 $r $ref_type
    }

    # DRI
    # When reference r is closed, copy its (integer) value in v
    proc dereference_integer { v r } {
        rule $r "dereference_integer_body $v $r" \
            name "DRI-$v-$r"
    }
    proc dereference_integer_body { v r } {
        # Get the TD from the reference
        set id [ adlb::acquire_ref $r ref 1 1 ]
        copy_integer $v $id
    }

    # DRV
    # When reference r is closed, set v
    proc dereference_void { v r } {
        rule $r "dereference_void_body $v $r" \
            name "DRV-$v-$r"
    }
    proc dereference_void_body { v r } {
        set id [ adlb::acquire_ref $r ref 1 1 ]
        copy_void $v $id
    }

    # DRF
    # When reference r is closed, copy its (float) value into v
    proc dereference_float { v r } {
        rule $r "dereference_float_body $v $r" \
            name "DRF-$v-$r"
    }

    proc dereference_float_body { v r } {
        # Get the TD from the reference
        set id [ adlb::acquire_ref $r ref 1 1 ]
        copy_float $v $id
    }

    # DRS
    # When reference r is closed, copy its (string) value into v
    proc dereference_string { v r } {
        rule $r "dereference_string_body $v $r" \
            name "DRS-$v-$r"
    }
    proc dereference_string_body { v r } {
        set id [ adlb::acquire_ref $r ref 1 1 ]
        copy_string $v $id
    }

    # DRB
    # When reference r is closed, copy blob to v
    proc dereference_blob { v r } {
        rule $r "dereference_blob_body $v $r" \
            name "DRB-$v-$r"
    }
    proc dereference_blob_body { v r } {
        set id [ adlb::acquire_ref $r ref 1 1 ]
        copy_blob [ list $v ] [ list $id ]
    }

    # CRVL
    # When reference cr is closed, store d = (*cr)[i]
    # Blocks on cr
    # inputs: cr i d d_type
    #       cr is a reference to a container
    #       i is a literal int
    #       d is the destination ref
    #       d_type is the turbine type name for representation of d
    # outputs: ignored
    proc cr_v_lookup { cr i d d_type } {
        log "creating reference: <*$cr>\[$i\] <- <*$d>"

        rule $cr "cr_v_lookup_body $cr $i $d $d_type" \
            name "CRVL-$cr"
    }

    proc cr_v_lookup_body { cr i d d_type } {
        # When this procedure is run, cr should be set and
        # i should be the literal index
        set c [ adlb::acquire_ref $cr ref 1 1 ]
        container_reference $c $i $d $d_type
    }

    # CRFL
    # When reference cr is closed, store d = (*cr)[i]
    # Blocks on cr and i
    # inputs:
    #       cr: reference to container
    #       i:  subscript (any type)
    #       d is the destination ref
    #       d_type is the turbine type name for representation of d
    # outputs: ignored
    proc cr_f_lookup { cr i d d_type } {
        rule "$cr $i" "cr_f_lookup_body $cr $i $d $d_type" \
            name "CRFL-$cr"
    }

    proc cr_f_lookup_body { cr i d d_type } {
        # When this procedure is run, cr and i should be set
        set c [ adlb::acquire_ref $cr ref 1 1 ]
        set t1 [ retrieve_decr $i ]
        container_reference $c $t1 $d $d_type
    }

    # CRFI
    # When reference r on c[i] is closed, store c[i][j] = d
    # Blocks on r and j
    # inputs: r j d
    # outputs: ignored
    proc cr_f_insert {cr j d t} {
        log "insert (future): <*$cr>\[<$j>\]=<$d>"

        rule "$cr $j" "cr_f_insert_body $cr $j $d $t" \
            name "CRFI-$cr"
    }
    proc cr_f_insert_body { cr j d t } {
        # s: The subscripted container
        set c [ adlb::acquire_write_ref $cr ref 1 1 1 ]
        set s [ retrieve_decr $j ]
        container_insert $c $s $d $t 1 1
        log "insert: (now) <$c>\[$s\]=<$d>"
    }

    # CRVI
    # When reference cr on c[i] is closed, store c[i][j] = d
    # Blocks on cr, j must be a tcl integer
    # inputs: r j d
    # outputs: ignored
    proc cr_v_insert { cr j d t } {
        rule "$cr" "cr_v_insert_body $cr $j $d $t" \
            name "CRVI-$cr-$j-$d"
    }
    proc cr_v_insert_body { cr j d t } {
        set c [ adlb::acquire_write_ref $cr ref 1 1 1 ]
        # insert and drop slot
        container_insert $c $j $d $t 1 1
    }

    # CRVIR
    # j: tcl integer index
    proc cr_v_insert_r { cr j dr t } {
        rule [ list $cr $dr ] \
            "cr_v_insert_r_body $cr $j $dr $t" \
            name "CRVIR"
    }
    proc cr_v_insert_r_body { cr j dr t } {
        set c [ adlb::acquire_write_ref $cr ref 1 1 1 ]
        set d [ adlb::acquire_ref $dr $t 1 1 ]
        container_insert $c $j $d $t 1 1
    }

    proc cr_f_insert_r { cr j dr t } {
        rule [ list $cr $j $dr ] \
            "cr_f_insert_r_body $cr $j $dr $t" \
            name "CRFIR"
    }
    proc cr_f_insert_r_body { cr j dr t } {
        set c [ adlb::acquire_write_ref $cr ref 1 1 1 ]
        set d [ adlb::acquire_ref $dr $t 1 1 ]
        set jval [ retrieve_decr $j ]
        # Insert and drop refcounts we acquired
        container_insert $c $jval $d $t 1 1
    }

    # CVC
    # Create datum c[i] inside of container c
    # c[i] may already exist, if so, that's fine
    proc c_v_create { c i full_type {caller_read_ref 0} \
                {caller_write_ref 0} {decr_write 0} {decr_read 0}} {
      return [ create_nested $c $i {*}$full_type \
                        $caller_read_ref $caller_write_ref \
                        $decr_write $decr_read ]
    }
    
    # SC
    # Create datum s.f inside of struct s
    # s.f may already exist, if so, that's fine
    proc struct_create { s f full_type {caller_read_ref 0} \
                {caller_write_ref 0} {decr_write 0} {decr_read 0}} {
      return [ struct_create_nested $s $f {*}$full_type \
                        $caller_read_ref $caller_write_ref \
                        $decr_write $decr_read ]
    }

    # CFC
    # puts a reference to a nested datum at c[i]
    # into reference variable r.
    # i: an integer future
    proc c_f_create { r c i full_type {decr_write 1} {decr_read 0}} {
        rule $i "c_f_create_body $r $c $i {$full_type} $decr_write $decr_read" \
            name "CFC-$r"
    }

    # Create container at c[i]
    # Set r, a reference TD on c[i]
    proc c_f_create_body { r c i full_type decr_write decr_read } {

        debug "c_f_create: $r $c\[$i\] $full_type"

        set s [ retrieve_decr $i ]
        # Acquire 1 read & 1 write refcount for container
        set res [ create_nested $c $s {*}$full_type 1 1 $decr_write $decr_read ]
        store_rw_ref $r $res
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc cr_v_create { r cr i full_type } {
        rule "$cr" \
          "cr_v_create_body $r $cr $i {$full_type}" \
           name crvc
    }

    proc cr_v_create_body { r cr i full_type } {
        set c [ adlb::acquire_write_ref $cr ref 1 1 1 ]
        # Transfer 1 read & write refcount to ref
        set res [ create_nested $c $i {*}$full_type 1 1 1 1 ]
        store_rw_ref $r $res
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc cr_f_create { r cr i full_type } {
        rule "$cr $i" "cr_f_create_body $r $cr $i {$full_type}" \
           name crfc
    }

    proc cr_f_create_body { r cr i full_type } {
        set c [ adlb::acquire_write_ref $cr ref 1 1 1 ]
        set s [ retrieve_decr $i ]
        # Transfer 1 read & write refcount to ref
        set res [ create_nested $c $s {*}$full_type 1 1 1 1 ]
        store_rw_ref $r $res
    }

    # When container is closed, concatenate its keys in result
    # container: The container to read
    # result: A string
    proc enumerate { result container } {
        rule $container \
            "enumerate_body $result $container" \
            name "enumerate-$result-$container"
    }

    proc enumerate_body { result container } {
        set s [ container_list $container ]
        store_string $result $s
    }

    # When container is closed, count the members
    # result: a turbine integer
    proc container_size { result container } {
        rule $container "container_size_body $result $container"
    }

    proc container_size_body { result container } {
        set sz [ adlb::container_size $container 1 ]
        store_integer $result $sz
    }

    proc container_size_local { container {read_decr 0} } {
      return [ adlb::container_size $container $read_decr ]
    }

    # When integer i is closed,
    #        return whether exists c[i]
    # Does not wait for container c to be closed.
    # result: a turbine integer, 0 if not present, 1 if true
    proc exists { result inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        rule "$i" "exists_body $result $c $i" \
            name "exists-$result-$c-$i"
    }

    proc exists_body { result c i } {
        set i_val [ turbine::retrieve_decr $i ]
        store_integer $result [ adlb::exists_sub $c $i_val 1 ]
    }

    # When container c and integer i are closed,
    #        return whether exists c[i]
    # result: a turbine integer, 0 if not present, 1 if true
    proc contains { result inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        rule "$c $i" "contains_body $result $c $i" \
            name "contains-$result-$c-$i"
    }

    proc contains_body { result c i } {
        set i_val [ turbine::retrieve_decr $i ]
        store_integer $result [ adlb::exists_sub $c $i_val 1 ]
    }

    # Dereference a struct reference, then copy out a struct member
    proc structref_reference { structr subscript result type \
      {write_decr 1} {read_transfer 1} {write_transfer 0}} {
        rule  "$structr" \
            "structref_reference_body $structr $subscript $result $type\
             $write_decr $read_transfer $write_transfer" \
            name "structref_reference-$structr-$subscript"
    }

    proc structref_reference_body { structr subscript result type
            write_decr read_transfer write_transfer } {
        set struct [ adlb::acquire_ref $structr ref 1 1 ]
        adlb::struct_reference $struct $subscript $result $type \
                    $write_decr $read_transfer $write_transfer
    }

    # Wait, recursively for container contents
    # Supports plain futures and files
    # inputs: list of tds to wait on
    # nest_levels: list corresponding to inputs with nesting level
    #             of containers
    # base_types: type of data in innermost of containers
    # action: command to execute when closed
    # args: additional keyword args (same as rule)
    proc deeprule { inputs nest_levels base_types action args } {
      # signals: list of variables that must be closed to signal deep closing
      # allocated_signals: signal variables that were allocated
      set signals [ list ]
      set allocated_signals [ list ]
      check { [ llength $inputs ] == [ llength $nest_levels ] } \
        "deeprule: list lengths do not agree: inputs and nest_levels"
      check { [ llength $inputs ] == [ llength $base_types ] } \
        "deeprule: list lengths do not agree: inputs and base_types"
      set i 0
      foreach input $inputs {
        set base_type [ lindex $base_types $i ]
        set nest_level [ lindex $nest_levels $i ]
        check { $nest_level >= 0 } \
            "deeprule: negative nest_level: $nest_level"
        if { $nest_level == 0 } {
          # Just need to wait on right thing
          set signal [ deeprule_basetype_signal $base_type $input \
                                                allocated_signals ]
          if { $signal != "" } {
            lappend signals $signal
          }
        } else {
          # Wait for deep close of container
          # Use void variable to signal recursive container closing
          set signal [ allocate void ]
          lappend signals $signal
          # make sure cleaned up later
          lappend allocated_signals $signal
          container_deep_wait $input $nest_level $base_type $signal
        }
        incr i
      }

      # Once all signals closed, run finalizer
      rule $signals \
          "deeprule_action \"$allocated_signals\" \"$action\"" \
          {*}$args
    }

    # Return thing that can be waited on to signal that base type is
    # recursively closed.  May require creating additional rules and
    # signal variables.  Returns empty string if no signal
    # allocated_signals: name of list tracking newly allocated signals
    proc deeprule_basetype_signal { base_type input allocated_signals_name } {
      switch -glob $base_type {
        file_ref {
          return [ get_file_status $input ]
        }
        ref {
          return $input
        }
        "struct *" {
          set signal [ deeprule_struct_signal $base_type $input ]
          upvar 1 $allocated_signals_name allocated_signals
          lappend allocated_signals $signal
          return $signal
        }
        "struct_ref *" {
          upvar 1 $allocated_signals_name allocated_signals
          return [ struct_ref_rec_deep_wait $input $base_type allocated_signals ]
        }
        default {
          # Assume basic scalar value - don't need to wait
          return ""
        }
      }
    }

    proc deeprule_struct_signal { base_type input } {
      set field_paths [ lindex $type 1 ]

      if { [ llength $field_paths ] == 0 } {
        return ""
      }

      set signal [ allocate void ]
      struct_rec_deep_wait $input $base_type $signal
      return $signal
    }

    proc deeprule_action { allocated_signals action } {
        deeprule_finish {*}$allocated_signals
        eval $action
    }

    # Check for container contents being closed and once true,
    # set signal
    # Called after container itself is closed
    proc container_deep_wait { container nest_level base_type signal } {
        debug "container_deep_wait: $container $nest_level"
        if { $nest_level == 1 } {
            switch -glob $base_type {
              ref -
              file_ref -
              "struct *" -
              "struct_ref *" {
                # Need to enumerate container elements
                set action [ list container_deep_wait_iterate $container \
                                      0 -1 $nest_level $base_type $signal ]
              }
              default {
                set action "store_void $signal"
              }
            }

        } else {
            set action [ list container_rec_deep_wait $container \
                                  $nest_level $base_type $signal ]
        }
        # Execute action after container is closed
        rule $container $action
    }

    proc container_deep_wait_iterate { container progress n
                                        nest_level base_type signal
                                        {allocated_signals {}} } {
      set MAX_CHUNK_SIZE 64
      # TODO: could divide and conquer instead of doing linear search
      if { $n == -1 } {
        set n [ adlb::container_size $container ]
      }

      free_allocated_signals $allocated_signals

      while { $progress < $n } {
        set chunk_size [ expr {min($MAX_CHUNK_SIZE, $n - $progress)} ]
        set members [ adlb::enumerate $container members \
                                      $chunk_size $progress ]
        foreach member $members {
          set allocated_signals [ list ]
          set member_signal [ deeprule_basetype_signal $base_type $member allocated_signals ]

          if { $member_signal == "" || [ adlb::closed $member_signal ] } {
            incr progress

            free_allocated_signals $allocated_signals
          } else {
            # Suspend execution until next item closed
            rule $member_signal [ list container_deep_wait_iterate $container \
                          $progress $n $nest_level $base_type $signal \
                          $allocated_signals ]
            return
          }
        }
      }
      # Finished
      log "Container <$container> deep closed"
      store_void $signal
    }

    proc free_allocated_signals { signals } {
      foreach signal $signals {
        read_refcount_decr $signal
      }
    }

    proc container_rec_deep_wait { container nest_level base_type signal } {
      set inner_signals [ list ]

      set members [ adlb::enumerate $container members all 0 ]
      if { [ llength $members ] == 0 } {
        # short-circuit
        store_void $signal
        return
      } elseif { [ llength $members ] == 1 } {
        # skip allocating new signal
        set inner [ lindex $members 0 ]
        container_deep_wait $inner [ expr {$nest_level - 1} ] $base_type \
                            $signal
      } else {
        foreach inner $members {
          set inner_signal [ allocate void ]
          lappend inner_signals $inner_signal
          container_deep_wait $inner [ expr {$nest_level - 1} ] $base_type \
                            $inner_signal
        }
        rule $inner_signals \
            "deeprule_fire_signal \"$inner_signals\" $signal"
      }
    }

    proc deeprule_fire_signal { inner_signals signal } {
        debug "deeprule_fire_signal: $inner_signals $signal"
        deeprule_finish {*}$inner_signals
        store_void $signal
    }

    # Cleanup allocated things for
    # Decrement references for signals
    proc deeprule_finish { args } {
        log "deeprule_finish: $args"
        foreach signal $args {
            read_refcount_decr $signal
        }
    }

    # Wait recursively on (not necessarily closed) struct with potentially
    # nested references inside it.
    # input: td or td/subscript of struct
    # type: list with struct type info -
    #      "struct_ref", fields, nest_levels, base_types
    #     field_paths: list of field path lists with recursive references
    #     nest_levels: list of nest levels for fields
    #     base_types: list of base types for fields
    # returns signal to wait for completion
    proc struct_ref_rec_deep_wait { input type allocated_signals_name } {
      set field_paths [ lindex $type 1 ]
      if { [ llength $field_paths ] == 0 } {
        # No need to recurse
        return $input
      }

      set signal [ allocate void ]

      upvar 1 $allocated_signals_name allocated_signals
      lappend allocated_signals $signal

      rule $input \
        [ list struct_ref_rec_deep_wait_ready $input $type $signal ]

      return $signal
    }

    proc struct_ref_rec_deep_wait_ready { input type signal } {
      struct_rec_deep_wait [ retrieve_struct $input ] $type $signal
    }

    # Recursively follow any references in struct and set signal
    proc struct_rec_deep_wait { value type signal } {
      set field_paths [ lindex $type 1 ]
      set field_nest_levels [ lindex $type 2 ]
      set field_base_types [ lindex $type 3 ]

      set field_values [ list ]
      foreach field_path $field_paths {
        lappend field_values [ dict get $value {*}$field_path ]
      }

      deeprule $field_values $field_nest_levels $field_base_types \
              "deeprule_fire_signal {} $signal"
    }

    # Given an ADLB container/bag/etc, retrieve values of everything
    # inside container.  Unpack into a dict or list as appropriate
    # types: list of nested types, from outer container to inner value
    #
    # E.g. valid type lists would be:
    # container int
    # container ref int
    # multiset container int
    # struct [ x integer y [ ref container int ] ]
    #
    # Consumes read refcounts from outer datum
    proc enumerate_rec { datum types {depth 0} {read_decr 0}} {
      set outer_type [ lindex $types $depth ]
      switch $outer_type {
        container {
          return [ enumerate_rec_container $datum $types $depth $read_decr 1 ]
        }
        multiset {
          return [ enumerate_rec_container $datum $types $depth $read_decr 0 ]
        }
        struct {
          return [ enumerate_rec_struct $datum $types $depth $read_decr ]
        }
        default {
          error "Expected compound type to enumerate: $outer_type"
        }
      }
    }

    proc enumerate_rec_struct { struct types depth read_decr } {
        set struct_val [ retrieve_struct $struct ]

        set result [ enumerate_rec_struct_val $struct_val $types $depth ]
        # Decrement here to avoid freeing contents earlier
        read_refcount_decr $struct $read_decr

        return $result
    }

    proc enumerate_rec_struct_val { struct_val types depth } {
        set field_types [ lindex $types [ expr {$depth + 1} ] ]
        set result_dict [ dict create ]

        dict for { key key_type } $field_types {
          set val [ dict get $struct_val $key ]
          set key_type_prefix [ lindex $key_type 0 ]
          switch $key_type_prefix {
            ref {
              # Recurse (skip ref prefix)
              set result_val [ enumerate_rec $val $key_type 1 0 ]
            }
            struct {
              # Check if we need to recurse
              if { [ llength $key_type ] > 1 } {
                set result_val [ enumerate_rec_struct_val $val $key_type 0 ]
              } else {
                set result_val $val
              }
            }
            default {
              set result_val $val
            }
          }

          dict append result_dict $key $result_val
        }

        return $result_dict
    }

    proc enumerate_rec_container { container types depth read_decr \
                                   include_keys } {
      set member_type [ lindex $types [ expr {$depth + 1} ] ]

      # If there is a ref type, followed by another type, we will
      # recurse to handle that.
      set ref_value [ string equal $member_type ref ]
      if { $ref_value } {
        set ref_root_type [ lindex $types [ expr {$depth + 2} ] ]
        if { [ llength $types ] > [ expr {$depth + 3} ] } {
          # Need to recurse further after retrieving member refs
          set recurse 1
        } else {
          # Just need to fetch members
          set recurse 0
          set member_ref_type [ lindex $types [ expr {$depth + 2} ] ]
        }
      }

      if { $include_keys } {
        if { $ref_value } {
          set vals [ adlb::enumerate $container dict all 0 0 ]
          set result_dict [ dict create ]

          if { $recurse } {
            dict for { key subcontainer } $vals {
              dict append result_dict $key [ enumerate_rec $subcontainer \
                    $types [ expr {$depth + 2} ] 0 ]
            }
          } else {
            # Optimization: do multiget on references
            set result_dict [ multi_retrieve_kv $vals CACHED 0 \
                              $member_ref_type ]
          }
          # Decrement here to avoid freeing contents
          read_refcount_decr $container $read_decr
          return $result_dict
        } else {
          # TODO: would need to recurse on inline structs, which are
          # currently unused in practice
          return [ adlb::enumerate $container dict all 0 $read_decr ]
        }
      } else {
        if { $ref_value } {
          set vals [ adlb::enumerate $container members all 0 0 ]
          set result_list [ list ]
          if { $recurse } {
            foreach subcontainer $vals {
              lappend result_dict [ enumerate_rec $subcontainer \
                                    $types [ expr {$depth + 2} ] 0 ]
            }
          } else {
            # Optimization: do multiget on references
            set result_dict [ multi_retrieve $vals CACHED 0 \
                              $member_ref_type ]
          }
          # Decrement here to avoid freeing contents
          read_refcount_decr $container $read_decr
          return $result_dict
        } else {
          # TODO: would need to recurse on inline structs, which are
          # currently unused in practice
          return [ adlb::enumerate $container members all 0 $read_decr ]
        }
      }
    }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
