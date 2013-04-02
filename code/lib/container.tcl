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

    # build array by inserting items into a container starting at 0
    # close: decrement writers count at end
    proc array_build { c elems close } {
      set n [ llength $elems ]
      log "container_build: <$c> $n elems, close $close"
      if { $n > 0 } {
        for { set i 0 } { $i < $n } { incr i } {
          set elem [ lindex $elems $i ]
          set drops 0
          if { [ expr {$close && $i == $n - 1 } ] } {
            set drops 1
          }
          adlb::insert $c $i $elem $drops
        }
      } else {
        adlb::slot_drop $c
      }
    }

    # Just like adlb::container_reference but add logging
    proc container_reference { c i r type } {
        log "creating reference: <$c>\[$i\] <- <*$r> ($type)"
        adlb::container_reference $c $i $r $type
        # TODO: need to move refcount from container to referenced item
        # once reference set
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
        rule $i "turbine::c_f_retrieve_integer_body $d $c $i" \
            name "CFRI-$c-$i" 
    }

    proc c_f_retrieve_integer_body { d c i } {
        set s [ retrieve $i ]
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
    # outputs: ignored.  To block on this, use turbine::reference
    # Note: assume slot kept open by other process
    proc c_f_insert { c i d {slot_drops 1} {slot_create 1}} {
        nonempty c i d

        if { $slot_create } {
            adlb::slot_create $c
        }

        rule $i [ list turbine::container_f_insert_body $c $i $d $slot_drops ] \
            name "CFI-$c-$i" 
    }

    proc container_f_insert_body { c i d slot_drops } {
        set s [ retrieve $i ]
        container_insert $c $s $d $slot_drops
    }

    # CFIR
    # When i and r are closed, set c[i] := *(r)
    # inputs: c i r
    # r: a reference to a turbine ID
    proc c_f_insert_r { c i r {slot_drops 1} {slot_create 1}} {
        nonempty c i r

        if { $slot_create } {
            adlb::slot_create $c
        }

        rule "$i $r" \
            "turbine::c_f_insert_r_body $c $i $r $slot_drops" \
            name "CFIR-$c-$i" 
    }

    proc c_f_insert_r_body { c i r slot_drops } {
        set t1 [ retrieve_decr_integer $i ]
        set d [ retrieve_decr $r ]
        container_insert $c $t1 $d $slot_drops
    }

    # CVIR
    # When r is closed, set c[i] := *(r)
    # inputs: c i r
    # i: an integer which is the index to insert into
    # r: a reference to a turbine ID
    proc c_v_insert_r { c i r {slot_drops 1} {slot_create 1}} {
        nonempty c i r

        if { $slot_create } {
            adlb::slot_create $c
        }

        rule $r "turbine::c_v_insert_r_body $c $i $r $slot_drops" \
            name "container_deref_insert-$c-$i" 
    }

    proc c_v_insert_r_body { c i r slot_drops } {
        set d [ retrieve_decr $r ]
        # Refcount from reference passed to container
        container_insert $c $i $d $slot_drops
    }

    # Immediately insert data into container without affecting open slot count
    # c: the container
    # i: the subscript
    # d: the data
    # outputs: ignored.
    proc container_immediate_insert { c i d {drops 0} } {
        # adlb::slot_create $c
        container_insert $c $i $d $drops
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

        rule $i "turbine::c_f_lookup_body $c $i $r $ref_type" \
            name "CFL-$c-$i" 
    }
    proc c_f_lookup_body { c i r ref_type } {
        debug "f_reference_body: <$c>\[<$i>\] <- <*$r>"
        set t1 [ retrieve $i ]
        debug "f_reference_body: <$c>\[$t1\] <- <$r>"
        container_reference $c $t1 $r $ref_type
    }

    # DRI
    # When reference r is closed, copy its (integer) value in v
    proc dereference_integer { v r } {
        rule $r "turbine::dereference_integer_body $v $r" \
            name "DRI-$v-$r" 
    }
    proc dereference_integer_body { v r } {
        # Get the TD from the reference
        set id [ retrieve_integer $r ]
        # When the TD has a value, copy the value
        read_refcount_incr $id
        copy_integer $v $id
    }

    # DRF
    # When reference r is closed, copy its (float) value into v
    proc dereference_float { v r } {
        rule $r "turbine::dereference_float_body $v $r" \
            name "DRF-$v-$r" 
    }

    proc dereference_float_body { v r } {
        # Get the TD from the reference
        set id [ retrieve_integer $r ]
        # When the TD has a value, copy the value
        read_refcount_incr $id
        copy_float $v $id
    }

    # DRS
    # When reference r is closed, copy its (string) value into v
    proc dereference_string { v r } {
        rule $r "turbine::dereference_string_body $v $r" \
            name "DRS-$v-$r" 
    }
    proc f_dereference_string_body { v r } {
        # Get the TD from the reference
        set id [ retrieve_integer $r ]
        # When the TD has a value, copy the value
        read_refcount_incr $id
        copy_string $v $id
    }

    # DRB
    # When reference r is closed, copy blob to v
    proc dereference_blob { v r } {
        rule $r [ list turbine::dereference_blob_body $v $r ] \
            name "DRB-$v-$r" 
    }
    proc dereference_blob_body { v r } {
        # Get the TD from the reference
        set handle [ retrieve_integer $r ]
        # When the TD has a value, copy the value
        read_refcount_incr $handle
        copy_blob [ list $v ] [ list $handle ]
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

        rule $cr "turbine::cr_v_lookup_body $cr $i $d $d_type" \
            name "CRVL-$cr" 
    }

    proc cr_v_lookup_body { cr i d d_type } {
        # When this procedure is run, cr should be set and
        # i should be the literal index
        set c [ retrieve_integer $cr ]
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
        rule "$cr $i" "turbine::cr_f_lookup_body $cr $i $d $d_type" \
            name "CRFL-$cr" 
    }

    proc cr_f_lookup_body { cr i d d_type } {
        # When this procedure is run, cr and i should be set
        set c [ retrieve_integer $cr ]
        set t1 [ retrieve $i ]
        container_reference $c $t1 $d $d_type
    }

    # CRFI
    # When reference r on c[i] is closed, store c[i][j] = d
    # Blocks on r and j
    # oc is outer container
    # inputs: r j d oc
    # outputs: ignored
    proc cr_f_insert { r j d oc {slot_create 1}} {
        log "insert (future): <*$r>\[<$j>\]=<$d>"

        if { $slot_create } {
            adlb::slot_create $oc
        }

        rule "$r $j" \
            [ list turbine::cr_f_insert_body $r $j $d $oc ] \
            name "CRFI-$r" 
    }
    proc cr_f_insert_body { r j d oc } {
        # s: The subscripted container
        set c [ retrieve_decr_integer $r ]
        set s [ retrieve_decr_integer $j ]
        container_insert $c $s $d
        log "insert: (now) <$c>\[$s\]=<$d>"
        adlb::slot_drop $oc
    }

    # CRVI
    # When reference cr on c[i] is closed, store c[i][j] = d
    # Blocks on cr, j must be a tcl integer
    # oc is a direct handle to the top-level container
    #       which cr will be inside
    # inputs: r j d oc
    # outputs: ignored
    proc cr_v_insert { cr j d oc {slot_create 1} } {
        if { $slot_create } {
            adlb::slot_create $oc
        }

        rule "$cr" \
            [ list turbine::cr_v_insert_body $cr $j $d $oc ] \
            name "CRVI-$cr-$j-$d-$oc" 
    }
    proc cr_v_insert_body { cr j d oc } {
        set c [ retrieve_decr_integer $cr ]
        # insert and drop slot
        container_insert $c $j $d
        adlb::slot_drop $oc
    }

    # CRFIR
    # j: tcl integer index
    # oc: direct handle to outer container
    proc cr_v_insert_r { cr j dr oc {slot_create 1}} {
        if { $slot_create } {
            adlb::slot_create $oc
        }

        rule [ list $cr $dr ] \
            "turbine::cr_v_insert_r_body $cr $j $dr $oc" \
            [ name "CRVIR"
    }
    proc cr_v_insert_body { cr j dr oc } {
        set c [ retrieve_decr_integer $cr ]
        set d [ retrieve_decr $dr ]
        #TODO: how to handle refcounting for referenced var
        container_insert $c $j $d
        adlb::slot_drop $oc
    }

    proc cr_f_insert_r { cr j dr oc {slot_create 1}} {
        if { $slot_create } {
            adlb::slot_create $oc
        }

        rule [ list $cr $j $dr ] \
            "turbine::cr_f_insert_r_body $cr $j $dr $oc" \
            name "CRFIR" 
    }
    proc cr_f_insert_r_body { cr j dr oc } {
        set c [ retrieve_decr_integer $cr ]
        set d [ retrieve_decr $dr ]
        set jval [ retrieve_decr_integer $j ]
        container_insert $c $jval $d
        adlb::slot_drop $oc
    }


        # UNUSED?
        # Insert c[i][j] = d
    proc f_container_nested_insert { c i j d } {

        rule "$i $j" [ list f_container_nested_insert_body_1 $c $i $j $d ] \
            name "fcni" 
    }

    proc f_container_nested_insert_body_1 { c i j d } {

        if [ container_insert_atomic $c $i ] {
            # c[i] does not exist
            set t [ data_new ]
            allocate_container t integer
            container_insert $c $i $t
        } else {
            allocate r integer
            container_reference $r $c $i "integer"

            rule "$r" \
                "container_nested_insert_body_2 $r $j $d" name fcnib 
        }
    }

    proc f_container_nested_insert_body_2 { r j d } {
        container_insert $r $j $d
    }

    # CVC
    # Create container c[i] inside of container c
    # c[i] may already exist, if so, that's fine
    proc c_v_create { c i type {decr_slots 0}} {
      log "creating nested container: <$c>\[$i\] ($type)"
      if [ adlb::insert_atomic $c $i ] {
        debug "<$c>\[$i\] doesn't exist, creating"
        # Member did not exist: create it and get reference
        allocate_container t $type

        # Add refcount - 1 for container, 1 for returned ref
        read_refcount_incr $t
        adlb::insert $c $i $t $decr_slots

        # setup rule to close when outer container closes
        rule $c "adlb::slot_drop $t" name "autoclose-$t" 
        return $t
      } else {
        # Another engine is creating it right this second, poll
        # until we get it.  Note: this should require at most one
        # or two polls to get a result
        debug "<$c>\[$i\] already exists, retrieving"
        set container_id 0
        while { $container_id == 0 } {
          set container_id [ adlb::lookup $c $i ]
        }
        # add to refcount for returned container ref
        read_refcount_incr $container_id
        # drop slot on outer
        if { $decr_slots } {
            adlb::slot_drop $c $decr_slots
        }
        return $container_id
      }
    }

    # CFC
    # puts a reference to a nested container at c[i]
    # into reference variable r.
    # i: an integer future
    proc c_f_create { r c i type {slot_create 1}} {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r


        rule $i "c_f_create_body $tmp_r $c $i $type" \
            name "CFC-$r" 
    }

    # Create container at c[i]
    # Set r, a reference TD on c[i]
    proc c_f_create_body { r c i type } {

        debug "c_f_create: $r $c\[$i\] $type"

        set s [ retrieve $i ]
        set res [ c_v_create $c $s $type ]
        store_integer $r $res
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    # oa: outer array
    # slot_create: if false, caller has created slot on oa
    proc cr_v_create { r cr i type oa {slot_create 1}} {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r

        if { $slot_create } {
            # create slot on outer array
            adlb::slot_create $oa
        }

        rule "$cr" "cr_v_create_body $tmp_r $cr $i $type $oa" \
           name crvc
    }

    proc cr_v_create_body { r cr i type oa } {
        set c [ retrieve_decr_integer $cr ]
        set res [ c_v_create $c $i $type ]
        store_integer $r $res
        adlb::slot_drop $oa
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    # oa: outer array of nested
    # slot_create: if false, caller has created slot on oa
    proc cr_f_create { r cr i type oa {slot_create 1}} {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r

        if { $slot_create } {
            # create slot on outer array
            adlb::slot_create $oa
        }

        rule "$cr $i" "cr_f_create_body $tmp_r $cr $i $type $oa" \
           name crfc
    }

    proc cr_f_create_body { r cr i type oa } {
        set c [ retrieve_decr_integer $cr ]
        set s [ retrieve_decr $i ]
        set res [ c_v_create $c $s $type ]
        store_integer $r $res
        adlb::slot_drop $oa
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
        set sz [ adlb::enumerate $container count all 0 ]
        store_integer $result $sz
    }

    proc container_size_local { container } {
      return [ adlb::enumerate $container count all 0 ]
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
        set i_val [ turbine::retrieve_integer $i ]
        set res [ container_lookup $c $i_val ]
        if { $res == 0 } {
            set exists 0
        } else {
            set exists 1
        }
        store_integer $result $exists
    }

    # If a reference to a struct is represented as a Turbine string
    # future containing a serialized TCL dict, then lookup a
    # struct member
    proc struct_ref_lookup { structr field result type } {
        rule  "$structr" "struct_ref_lookup_body $structr $field $result $type" \
            name "struct_ref_lookup-$structr" 
    }

    proc struct_ref_lookup_body { structr field result type } {
        set struct_val [ retrieve_string $structr ]
        debug "<${result}> <= \{ ${struct_val} \}.${field}"
        set result_val [ dict get $struct_val $field ]
        if { $type == "integer" } {
            store_integer $result $result_val
        } elseif { $type == "string" } {
            store_string $result $result_val
        } else {
            error "Unknown reference representation type $type"
        }
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
          lappend allocated_signals $signal # make sure cleaned up later
          container_deep_wait $input $nest_level $isf $signal
        }
        incr i
      }

      # Once all signals closed, run finalizer
      rule $signals "deeprule_finish $allocated_signals; $action" {*}args
    }

    # Check for container contents being closed and once true,
    # set signal
    # Called after container itself is closed
    proc container_deep_wait { container nest_level is_file signal } {
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
        set n [ adlb::enumerate $container count all 0 ]
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
          container_deep_wait $inner \ [ expr {$nest_level - 1} ] $is_file \
                            $inner_signal
        }
        rule $inner_signals 
          [ list deeprule_finish $inner_signals [ list store_void $signal ] ]
      }
    }

    # Cleanup allocated things for
    # Decrement references for signals
    proc deeprule_finish { args } {
      foreach signal $args {
        read_refcount_decr $signal
      }
      eval $cmd
    }
}
