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
    namespace export c_f_lookup

    namespace import ::turbine::c::create_nested

    # build array by inserting items into a container starting at 0
    # close: decrement writers count at end
    # val_type: type of array values
    proc array_build { c elems close val_type } {
      set n [ llength $elems ]
      log "array_build: <$c> $n elems, close $close"
      if { $n > 0 } {
        for { set i 0 } { $i < $n } { incr i } {
          set elem [ lindex $elems $i ]
          set drops 0
          if { [ expr {$close && $i == $n - 1 } ] } {
            set drops 1
          }
          adlb::insert $c $i $elem $val_type $drops
        }
      } else {
        adlb::write_refcount_decr $c
      }
    }

    # build array by inserting items into a container starting at 0
    # close: decrement writers count at end
    # val_type: type of array values
    proc array_kv_build { c keys vals close val_type } {
      set n [ llength $keys ]
      log "array_kv_build: <$c> $n elems, close $close"
      if { $n > 0 } {
        for { set i 0 } { $i < $n } { incr i } {
          set key [ lindex $keys $i ]
          set val [ lindex $vals $i ]
          set drops 0
          if { [ expr {$close && $i == $n - 1 } ] } {
            set drops 1
          }
          adlb::insert $c $key $val $val_type $drops
        }
      } else {
        adlb::write_refcount_decr $c
      }
    }

    # Just like adlb::container_reference but add logging
    # Note that container_reference always consumes a read reference count
    proc container_reference { c i r type } {
        log "creating reference: <$c>\[\"$i\"\] <- <*$r> ($type)"
        adlb::container_reference $c $i $r $type
    }

    # Same as container_lookup, but fail if item does not exist
    proc container_lookup_checked { c i } {
        set res [ container_lookup $c $i ]
        if { $res == 0 } {
            error "lookup failed: container_lookup <$c>\[$i\]"
        }
        return $res
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
            adlb::write_refcount_incr $c
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
            adlb::write_refcount_incr $c
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
            adlb::write_refcount_incr $c
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
        # adlb::write_refcount_incr $c
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
        set id [ acquire_ref $r 1 1 ]
        copy_integer $v $id
    }

    # DRF
    # When reference r is closed, copy its (float) value into v
    proc dereference_float { v r } {
        rule $r "dereference_float_body $v $r" \
            name "DRF-$v-$r"
    }

    proc dereference_float_body { v r } {
        # Get the TD from the reference
        set id [ acquire_ref $r 1 1 ]
        copy_float $v $id
    }

    # DRS
    # When reference r is closed, copy its (string) value into v
    proc dereference_string { v r } {
        rule $r "dereference_string_body $v $r" \
            name "DRS-$v-$r"
    }
    proc dereference_string_body { v r } {
        set id [ acquire_ref $r 1 1 ]
        copy_string $v $id
    }

    # DRB
    # When reference r is closed, copy blob to v
    proc dereference_blob { v r } {
        rule $r "dereference_blob_body $v $r" \
            name "DRB-$v-$r"
    }
    proc dereference_blob_body { v r } {
        set id [ acquire_ref $r 1 1 ]
        copy_blob [ list $v ] [ list $id ]
    }

    proc dereference_file { v r } {
        rule $r "dereference_file_body {$v} $r" \
            name "dereference_file"
    }
    proc dereference_file_body { v r } {
        # Get the TD from the reference
        set handle [ acquire_file_ref $r 1 1 ]
        copy_file [ list $v ] [ list $handle ]
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
        set c [ acquire_ref $cr 1 1 ]
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
        set c [ acquire_ref $cr 1 1 ]
        set t1 [ retrieve_decr $i ]
        container_reference $c $t1 $d $d_type
    }

    # CRFI
    # When reference r on c[i] is closed, store c[i][j] = d
    # Blocks on r and j
    # oc is outer container
    # inputs: r j d oc
    # outputs: ignored
    proc cr_f_insert {cr j d t oc {write_refcount_incr 1}} {
        log "insert (future): <*$cr>\[<$j>\]=<$d>"

        if { $write_refcount_incr } {
            adlb::write_refcount_incr $oc
        }

        rule "$cr $j" "cr_f_insert_body $cr $j $d $t $oc" \
            name "CRFI-$cr"
    }
    proc cr_f_insert_body { cr j d t oc } {
        # s: The subscripted container
        # don't need read reference
        set c [ acquire_ref $cr 0 1 ]
        set s [ retrieve_decr $j ]
        container_insert $c $s $d $t
        log "insert: (now) <$c>\[$s\]=<$d>"
        adlb::write_refcount_decr $oc
    }

    # CRVI
    # When reference cr on c[i] is closed, store c[i][j] = d
    # Blocks on cr, j must be a tcl integer
    # oc is a direct handle to the top-level container
    #       which cr will be inside
    # inputs: r j d oc
    # outputs: ignored
    proc cr_v_insert { cr j d t oc {write_refcount_incr 1} } {
        if { $write_refcount_incr } {
            adlb::write_refcount_incr $oc
        }

        rule "$cr" "cr_v_insert_body $cr $j $d $t $oc" \
            name "CRVI-$cr-$j-$d-$oc"
    }
    proc cr_v_insert_body { cr j d t oc } {
        # don't need read reference
        set c [ acquire_ref $cr 0 1 ]
        # insert and drop slot
        container_insert $c $j $d $t
        adlb::write_refcount_decr $oc
    }

    # CRVIR
    # j: tcl integer index
    # oc: direct handle to outer container
    proc cr_v_insert_r { cr j dr t oc {write_refcount_incr 1}} {
        if { $write_refcount_incr } {
            adlb::write_refcount_incr $oc
        }

        rule [ list $cr $dr ] \
            "cr_v_insert_r_body $cr $j $dr $t $oc" \
            name "CRVIR"
    }
    proc cr_v_insert_r_body { cr j dr t oc } {
        set c [ acquire_ref $cr 0 1 ]
        set d [ adlb::acquire_ref $dr $t 1 1 ]
        container_insert $c $j $d $t
        adlb::write_refcount_decr $oc
    }

    proc cr_f_insert_r { cr j dr t oc {write_refcount_incr 1}} {
        if { $write_refcount_incr } {
            adlb::write_refcount_incr $oc
        }

        rule [ list $cr $j $dr ] \
            "cr_f_insert_r_body $cr $j $dr $t $oc" \
            name "CRFIR"
    }
    proc cr_f_insert_r_body { cr j dr t oc } {
        set c [ acquire_ref $cr 1 1 ]
        set d [ adlb::acquire_ref $dr $t 1 1 ]
        set jval [ retrieve_decr $j ]
        container_insert $c $jval $d $t
        adlb::write_refcount_decr $oc
    }

    # CVC
    # Create container c[i] inside of container c
    # c[i] may already exist, if so, that's fine
    proc c_v_create { c i key_type val_type {caller_read_ref 0} \
                {caller_write_ref 0} {decr_write 0} {decr_read 0}} {
      return [ create_nested $c $i $key_type $val_type \
                        $caller_read_ref $caller_write_ref \
                        $decr_write $decr_read ]
    }

    # CFC
    # puts a reference to a nested container at c[i]
    # into reference variable r.
    # i: an integer future
    proc c_f_create { r c i key_type val_type {decr_write 1} {decr_read 0}} {
        rule $i "c_f_create_body $r $c $i $key_type $val_type $decr_write $decr_read" \
            name "CFC-$r"
    }

    # Create container at c[i]
    # Set r, a reference TD on c[i]
    proc c_f_create_body { r c i key_type val_type decr_write decr_read } {

        debug "c_f_create: $r $c\[$i\] $key_type $val_type"

        set s [ retrieve_decr $i ]
        # Acquire 1 read refcount for container
        set res [ create_nested $c $s $key_type $val_type 1 0 $decr_write $decr_read ]
        store_ref $r $res
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    # oa: outer array
    # write_refcount_incr: if false, caller has created slot on oa
    proc cr_v_create { r cr i key_type val_type oa {write_refcount_incr 1}} {
        if { $write_refcount_incr } {
            # create slot on outer array
            adlb::write_refcount_incr $oa
        }

        rule "$cr" \
          "cr_v_create_body $r $cr $i $key_type $val_type $oa" \
           name crvc
    }

    proc cr_v_create_body { r cr i key_type val_type oa } {
        set c [ acquire_ref $cr 1 1 ]
        # Acquire 1 read refcount for container
        set res [ create_nested $c $i $key_type $val_type 1 0 0 1 ]
        # Pass read refcount into ref
        store_ref $r $res
        adlb::write_refcount_decr $oa
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    # oa: outer array of nested
    # write_refcount_incr: if false, caller has created slot on oa
    proc cr_f_create { r cr i key_type val_type oa {write_refcount_incr 1}} {
        if { $write_refcount_incr } {
            # create slot on outer array
            adlb::write_refcount_incr $oa
        }

        rule "$cr $i" "cr_f_create_body $r $cr $i $key_type $val_type $oa" \
           name crfc
    }

    proc cr_f_create_body { r cr i key_type val_type oa } {
        set c [ acquire_ref $cr 1 1 ]
        set s [ retrieve_decr $i ]
        # Acquire 1 read refcount for container
        set res [ create_nested $c $s $key_type $val_type 1 0 0 1 ]
        store_ref $r $res
        adlb::write_refcount_decr $oa
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

    proc container_size_local { container } {
      return [ adlb::container_size $container ]
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

    # If a reference to a struct is represented as a Turbine string
    # future containing a serialized TCL dict, then lookup a
    # struct member
    proc struct_ref_lookup { structr field_id result type } {
        rule  "$structr" \
            "struct_ref_lookup_body $structr $field_id $result $type" \
            name "struct_ref_lookup-$structr-$field_id"
    }

    proc struct_ref_lookup_body { structr field_id result type } {
        set result_val [ acquire_subscript $structr $field_id $type 1 1 ]
        debug "<${result}> <= ${result_val}"
        adlb::store $result $type $result_val
    }

    # Wait, recursively for container contents
    # Supports plain futures and files
    # inputs: list of tds to wait on
    # nest_levels: list corresponding to inputs with nesting level
    #             of containers
    # is_file: list of booleans: whether file
    # action: command to execute when closed
    # args: additional keyword args (same as rule)
    proc deeprule { inputs nest_levels is_file action args } {
      # signals: list of variables that must be closed to signal deep closing
      # allocated_signals: signal variables that were allocated
      set signals [ list ]
      set allocated_signals [ list ]
      set i 0
      foreach input $inputs {
        set isf [ lindex $is_file $i ]
        set nest_level [ lindex $nest_levels $i ]
        if { $nest_level < 0 } {
          error "nest_level $nest_level must be non-negative"
        }
        if { $nest_level == 0 } {
          # Just need to wait on right thing
          if { $isf } {
            lappend signals [ get_file_status $input ]
          } else {
            lappend signals $input
          }
        } else {
          # Wait for deep close of container
          # Use void variable to signal recursive container closing
          set signal [ allocate void ]
          lappend signals $signal
          # make sure cleaned up later
          lappend allocated_signals $signal
          container_deep_wait $input $nest_level $isf $signal
        }
        incr i
      }

      # Once all signals closed, run finalizer
      rule $signals \
          "deeprule_action \"$allocated_signals\" \"$action\"" \
          {*}$args
    }

    proc deeprule_action { allocated_signals action } {
        deeprule_finish {*}$allocated_signals
        eval $action
    }

    # Check for container contents being closed and once true,
    # set signal
    # Called after container itself is closed
    proc container_deep_wait { container nest_level is_file signal } {

        debug "container_deep_wait: $container $nest_level"
        if { $nest_level == 1 } {
            # First wait for container to be closed
            rule $container [ list container_deep_wait_continue $container \
                                  0 -1 $nest_level $is_file $signal ]
        } else {
            rule $container [ list container_rec_deep_wait $container \
                                  $nest_level $is_file $signal ]
        }
    }

    proc container_deep_wait_continue { container progress n
                                        nest_level is_file signal } {
      set MAX_CHUNK_SIZE 64
      # TODO: could divide and conquer instead of doing linear search
      if { $n == -1 } {
        set n [ adlb::container_size $container ]
      }
      while { $progress < $n } {
        set chunk_size [ expr {min($MAX_CHUNK_SIZE, $n - $progress)} ]
        set members [ adlb::enumerate $container members \
                                      $chunk_size $progress ]
        foreach member $members {
          if {$is_file} {
            set td [ get_file_status $member ]
          } else {
            set td $member
          }
          if { [ adlb::exists $td ] } {
            incr progress
          } else {
            # Suspend execution until next item closed
            rule $td [ list container_deep_wait_continue $container \
                          $progress $n $nest_level $is_file $signal ]
            return
          }
        }
      }
      # Finished
      log "Container <$container> deep closed"
      store_void $signal
    }

    proc container_rec_deep_wait { container nest_level is_file signal } {
      set inner_signals [ list ]

      set members [ adlb::enumerate $container members all 0 ]
      if { [ llength $members ] == 0 } {
        # short-circuit
        store_void $signal
        return
      } elseif { [ llength $members ] == 1 } {
        # skip allocating new signal
        set inner [ lindex $members 0 ]
        container_deep_wait $inner [ expr {$nest_level - 1} ] $is_file \
                            $signal
      } else {
        foreach inner $members {
          set inner_signal [ allocate void ]
          lappend inner_signals $inner_signal
          container_deep_wait $inner [ expr {$nest_level - 1} ] $is_file \
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
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
