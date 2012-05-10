# Turbine builtin container operations

namespace eval turbine {
    namespace export container_f_get container_f_insert
    namespace export f_reference
    namespace export f_container_create_nested


    # Same as container_lookup, but fail if item does not exist
    proc container_lookup_checked { c i } {
        set res [ container_lookup $c $i ]
        if { $res == 0 } {
            error "lookup failed: container_lookup <$c>\[$i\]"
        }
        return $res
    }

    # When i is closed, set d := c[i] (by value copy)
    # d: the destination, an integer
    # inputs: [ list c i ]
    # c: the container
    # i: the subscript (any type)
    proc container_f_get_integer { parent d inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]

        rule "container_f_get-$c-$i" $i $turbine::LOCAL \
            "turbine::container_f_get_integer_body $d $c $i"
    }

    proc container_f_get_integer_body { d c i } {
        set s [ retrieve $i ]
        set t [ container_lookup $c $s ]
        if { $t == 0 } {
            error "lookup failed: container_f_get <$c>\[\"$s\"\]"
        }
        set value [ retrieve_integer $t ]
        store_integer $d $value
    }

    # When i is closed, set c[i] := d (by insertion)
    # inputs: [ list c i d ]
    # c: the container
    # i: the subscript (any type)
    # d: the data
    # outputs: ignored.  To block on this, use turbine::reference
    proc container_f_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        nonempty c i d
        adlb::slot_create $c

        rule "container_f_insert-$c-$i" $i $turbine::LOCAL \
            "turbine::container_f_insert_body $c $i $d"
    }

    proc container_f_insert_body { c i d } {
        set s [ retrieve $i ]
        container_insert $c $s $d 1
    }

    # When i and r are closed, set c[i] := *(r)
    # inputs: [ list c i r ]
    # r: a reference to a turbine ID
    proc container_f_deref_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]

        nonempty c i r
        adlb::slot_create $c

        rule "container_f_deref_insert-$c-$i" "$i $r" $turbine::LOCAL \
            "turbine::container_f_deref_insert_body $c $i $r"
    }

    proc container_f_deref_insert_body { c i r } {
        set t1 [ retrieve_integer $i ]
        set d [ retrieve $r ]
        container_insert $c $t1 $d
    }

    # When r is closed, set c[i] := *(r)
    # inputs: [ list c i r ]
    # i: an integer which is the index to insert into
    # r: a reference to a turbine ID
    proc container_deref_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]

        nonempty c i r
        adlb::slot_create $c

        rule "container_deref_insert-$c-$i" "$r" $turbine::LOCAL \
            "turbine::container_deref_insert_body $c $i $r"
    }

    proc container_deref_insert_body { c i r } {
        set d [ retrieve $r ]
        container_insert $c $i $d
    }

    # Immediately insert data into container without affecting open slot count
    # c: the container
    # i: the subscript
    # d: the data
    # outputs: ignored.
    proc container_immediate_insert { c i d } {
        # adlb::slot_create $c
        container_insert $c $i $d 0
    }

    # When i is closed, get a reference on c[i] in TD r
    # Thus, you can block on r and be notified when c[i] exists
    # r is an integer.  The value of r is the TD of c[i]
    # inputs: [ list c i r ]
    # outputs: None.  You can block on d with turbine::dereference
    # c: the container
    # i: the subscript (any type)
    # r: the reference TD
    proc f_reference { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]
        # nonempty c i r

        rule "f_reference_body-$c-$i" $i $turbine::LOCAL \
            "turbine::f_reference_body $c $i $r"
    }
    proc f_reference_body { c i r } {
        set t1 [ retrieve $i ]
        adlb::container_reference $c $t1 $r
    }

    # When reference r is closed, copy its (integer) value in v
    proc f_dereference_integer { parent v r } {

        rule "f_dereference-$v-$r" $r $turbine::LOCAL \
            "turbine::f_dereference_integer_body $v $r"
    }
    proc f_dereference_integer_body { v r } {
        # Get the TD from the reference
        set id [ retrieve_integer $r ]
        # When the TD has a value, copy the value
        copy_integer no_stack $v $id
    }

    # When reference r is closed, copy its (float) value into v
    proc f_dereference_float { parent v r } {
        rule "f_dereference-$v-$r" $r $turbine::LOCAL \
            "turbine::f_dereference_float_body $v $r"
    }

    proc f_dereference_float_body { v r } {
        # Get the TD from the reference
        set id [ retrieve_integer $r ]
        # When the TD has a value, copy the value
        copy_float no_stack $v $id
    }

    # When reference r is closed, copy its (string) value into v
    proc f_dereference_string { parent v r } {

        rule "f_dereference-$v-$r" $r $turbine::LOCAL \
            "turbine::f_dereference_string_body $v $r"
    }
    proc f_dereference_string_body { v r } {
        # Get the TD from the reference
        set id [ retrieve_integer $r ]
        # When the TD has a value, copy the value
        copy_string no_stack $v $id
    }

    # When reference cr is closed, store d = (*cr)[i]
    # Blocks on cr
    # inputs: [ list cr i d ]
    #       cr is a reference to a container
    #       i is a literal int
    # outputs: ignored
    proc f_cref_lookup_literal { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]

        rule "f_cref_lookup_literal-$cr" "$cr" $turbine::LOCAL \
            "turbine::f_cref_lookup_literal_body $cr $i $d"

    }

    proc f_cref_lookup_literal_body { cr i d } {
        # When this procedure is run, cr should be set and
        # i should be the literal index
        set c [ retrieve_integer $cr ]
        adlb::container_reference $c $i $d
    }

    # When reference cr is closed, store d = (*cr)[i]
    # Blocks on cr and i
    # inputs: [ list cr i d ]
    #       cr: reference to container
    #       i:  subscript (any type)
    # outputs: ignored
    proc f_cref_lookup { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]

        rule "f_cref_lookup-$cr" "$cr $i" $turbine::LOCAL \
            "turbine::f_cref_lookup_body $cr $i $d"
    }

    proc f_cref_lookup_body { cr i d } {
        # When this procedure is run, cr and i should be set
        set c [ retrieve_integer $cr ]
        set t1 [ retrieve $i ]
        adlb::container_reference $c $t1 $d
    }

    # When reference r on c[i] is closed, store c[i][j] = d
    # Blocks on r and j
    # oc is outer container
    # inputs: [ list r j d oc ]
    # outputs: ignored
    proc f_cref_insert { parent outputs inputs } {
        set r [ lindex $inputs 0 ]
        # set c [ lindex $inputs 1 ]
        set j [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set oc [ lindex $inputs 3 ]
        adlb::slot_create $oc

        rule "f_cref_insert-$r-$j-$d-$oc" "$r $j" $turbine::LOCAL \
            "turbine::f_cref_insert_body $r $j $d $oc"
    }
    proc f_cref_insert_body { r j d oc } {
        # s: The subscripted container
        set c [ retrieve_integer $r ]
        set s [ retrieve_integer $j ]
        container_insert $c $s $d
        adlb::slot_drop $oc
    }

    # When reference cr on c[i] is closed, store c[i][j] = d
    # Blocks on cr, j must be a tcl integer
    # oc is a direct handle to the top-level container
    #       which cr will be inside
    # inputs: [ list r j d oc ]
    # outputs: ignored
    proc cref_insert { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set j [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set oc [ lindex $inputs 3 ]
        adlb::slot_create $oc

        rule "cref_insert-$cr-$j-$d-$oc" "$cr" $turbine::LOCAL \
            "turbine::cref_insert_body $cr $j $d $oc"
    }
    proc cref_insert_body { cr j d oc } {
        set c [ retrieve_integer $cr ]
        # insert and drop slot
        container_insert $c $j $d
        adlb::slot_drop $oc
    }

    # j: tcl integer index
    # oc: direct handle to outer container
    proc cref_deref_insert { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set j [ lindex $inputs 1 ]
        set dr [ lindex $inputs 2 ]
        set oc [ lindex $inputs 3 ]
        adlb::slot_create $oc

        rule "cref_deref_insert-$cr-$j-$dr-$oc" "$cr $dr" $turbine::LOCAL \
            "turbine::cref_deref_insert_body $cr $j $dr $oc"
    }
    proc cref_deref_insert_body { cr j dr oc } {
        set c [ retrieve_integer $cr ]
        set d [ retrieve $dr ]
        container_insert $c $j $d
        adlb::slot_drop $oc
    }

    proc cref_f_deref_insert { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set j [ lindex $inputs 1 ]
        set dr [ lindex $inputs 2 ]
        set oc [ lindex $inputs 3 ]
        adlb::slot_create $oc

        rule "cref_f_deref_insert-$cr-$j-$dr-$oc" "$cr $j $dr" $turbine::LOCAL \
            "turbine::cref_f_deref_insert_body $cr $j $dr $oc"
    }
    proc cref_f_deref_insert_body { cr j dr oc } {
        set c [ retrieve_integer $cr ]
        set d [ retrieve $dr ]
        set jval [ retrieve_integer $j ]
        container_insert $c $jval $d
        adlb::slot_drop $oc
    }


    # Insert c[i][j] = d
    proc f_container_nested_insert { c i j d } {


        rule "fcni" "$i $j" $turbine::LOCAL \
            "f_container_nested_insert_body_1 $c $i $j $d"
    }

    proc f_container_nested_insert_body_1 { c i j d } {

        if [ container_insert_atomic $c $i ] {
            # c[i] does not exist
            set t [ data_new ]
            allocate_container t integer
            container_insert $c $i $t
        } else {
            allocate r integer
            container_reference $r $c $i

            rule fcnib "$r" $turbine::LOCAL \
                "container_nested_insert_body_2 $r $j $d"
        }
    }

    proc f_container_nested_insert_body_2 { r j d } {
        container_insert $r $j $d
    }

    proc container_create_nested { c i type } {
      debug "container_create_nested: $c\[$i\] $type"
      if [ adlb::insert_atomic $c $i ] {
        debug "$c\[$i\] doesn't exist, creating"
        # Member did not exist: create it and get reference
        allocate_container t $type
        adlb::insert $c $i $t
        # setup rule to close when outer container closes

        rule "autoclose-$c-$t" "$c" $turbine::LOCAL \
               "adlb::slot_drop $t"
        return $t
      } else {
        # Another engine is creating it right this second, poll
        # until we get it.  Note: this should require at most one
        # or two polls to get a result
        debug "$c\[$i\] already exists, retrieving"
        set container_id 0
        while { $container_id == 0 } {
          set container_id [ adlb::lookup $c $i ]
        }
        return $container_id
      }
    }

    # puts a reference to a nested container at c[i]
    # into reference variable r.
    # i: an integer future
    proc f_container_create_nested { r c i type } {

        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r


        rule fccn "$i" $turbine::LOCAL \
               "f_container_create_nested_body $tmp_r $c $i $type"
    }


    # Create container at c[i]
    # Set r, a reference TD on c[i]
    proc f_container_create_nested_body { r c i type } {

        debug "f_container_create_nested: $r $c\[$i\] $type"

        set s [ retrieve $i ]
        set res [ container_create_nested $c $s $type ]
        store_integer $r $res
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc cref_create_nested { r cr i type } {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r


        rule fcrcn "$cr" $turbine::LOCAL \
           "cref_create_nested_body $tmp_r $cr $i $type"
    }

    proc cref_create_nested_body { r cr i type } {
        set c [ retrieve_integer $cr ]
        set res [ container_create_nested $c $i $type ]
        store_integer $r $res
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc f_cref_create_nested { r cr i type } {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r


        rule fcrcn "$cr $i" $turbine::LOCAL \
           "f_cref_create_nested_body $tmp_r $cr $i $type"
    }

    proc f_cref_create_nested_body { r cr i type } {
        set c [ retrieve_integer $cr ]
        set s [ retrieve $i ]
        set res [ container_create_nested $c $s $type ]
        store_integer $r $res
    }

    # When container is closed, concatenate its keys in result
    # container: The container to read
    # result: An initialized string
    proc enumerate { stack result container } {

        rule "enumerate-$container" $container $turbine::LOCAL \
            "enumerate_body $result $container"
    }

    proc enumerate_body { result container } {
        set s [ container_list $container ]
        store_string $result $s
    }

    # When container is closed, count the members
    # result: a turbine integer
    proc container_size { stack result container } {

        rule "container_size-$container" $container $turbine::LOCAL \
            "container_size_body $result $container"
    }

    proc container_size_body { result container } {
        set sz [ adlb::enumerate $container count all 0 ]
        store_integer $result $sz
    }
}
