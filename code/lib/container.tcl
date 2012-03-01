# Turbine builtin container operations

namespace eval turbine {
    namespace export container_f_get container_f_insert
    namespace export f_reference
    namespace export f_container_create_nested

    # When i is closed, set d := c[i]
    # d: the destination, an integer
    # inputs: [ list c i ]
    # c: the container
    # i: the subscript
    proc container_f_get_integer { parent d inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set rule_id [ rule_new ]
        rule $rule_id "container_f_get-$c-$i" $i $d \
            "tl: turbine::container_f_get_integer_body $d $c $i"
    }

    proc container_f_get_integer_body { d c i } {
        set t1 [ get $i ]
        set t2 [ container_get $c $t1 ]
        if { $t2 == 0 } {
            error "lookup failed: container_f_get <$c>\[$t1\]"
        }
        set t3 [ get $t2 ]
        set_integer $d $t3
    }

    # When i is closed, set c[i] := d
    # inputs: [ list c i d ]
    # c: the container
    # i: the subscript
    # d: the data
    # outputs: ignored.  To block on this, use turbine::reference
    proc container_f_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        nonempty c i d
        adlb::slot_create $c
        set rule_id [ rule_new ]
        rule $rule_id "container_f_insert-$c-$i" $i "" \
            "tp: turbine::container_f_insert_body $c $i $d"
    }

    proc container_f_insert_body { c i d } {
        set t1 [ get $i ]
        container_insert $c $t1 $d
    }

    # When i and r are closed, set c[i] := *(r)
    # inputs: [ list c i r ]
    # r: a reference to a turbine ID
    #
    proc container_f_deref_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]

        nonempty c i r
        adlb::slot_create $c
        set rule_id [ rule_new ]
        rule $rule_id "container_f_deref_insert-$c-$i" "$i $r" "" \
            "tp: turbine::container_f_deref_insert_body $c $i $r"
    }

    proc container_f_deref_insert_body { c i r } {
        set t1 [ get $i ]
        set d [ get $r ]
        container_insert $c $t1 $d
    }

    # When r is closed, set c[i] := *(r)
    # inputs: [ list c i r ]
    # i: an integer which is the index to insert into
    # r: a reference to a turbine ID
    #
    proc container_deref_insert { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]

        nonempty c i r
        adlb::slot_create $c
        set rule_id [ rule_new ]
        rule $rule_id "container_deref_insert-$c-$i" "$r" "" \
            "tp: turbine::container_deref_insert_body $c $i $r"
    }

    proc container_deref_insert_body { c i r } {
        set d [ get $r ]
        container_insert $c $i $d
    }

    # Immediately insert data into container without affecting open slot count
    # c: the container
    # i: the subscript
    # d: the data
    # outputs: ignored.
    proc container_immediate_insert { c i d } {
        adlb::slot_create $c
        container_insert $c $i $d
    }

    # When i is closed, get a reference on c[i] in TD r
    # Thus, you can block on r and be notified when c[i] exists
    # r is an integer.  The value of r is the TD of c[i]
    # inputs: [ list c i r ]
    # outputs: None.  You can block on d with turbine::dereference
    # c: the container
    # i: the subscript
    # r: the reference TD
    proc f_reference { parent outputs inputs } {
        set c [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set r [ lindex $inputs 2 ]
        # nonempty c i r
        set rule_id [ rule_new ]
        rule $rule_id "f_reference_body-$c-$i" $i "" \
            "tp: turbine::f_reference_body $c $i $r"
    }
    proc f_reference_body { c i r } {
        set t1 [ get $i ]
        adlb::container_reference $c $t1 $r
    }

    # When reference r is closed, copy its (integer) value in v
    proc f_dereference_integer { parent v r } {
        set rule_id [ rule_new ]
        rule $rule_id "f_dereference-$v-$r" $r $v \
            "tp: turbine::f_dereference_integer_body $v $r"
    }
    proc f_dereference_integer_body { v r } {
        # Get the TD from the reference
        set id [ get $r ]
        # When the TD has a value, copy the value
        copy_integer no_stack $v $id
    }

    # When reference r is closed, copy its (float) value into v
    proc f_dereference_float { parent v r } {
        set rule_id [ rule_new ]
        rule $rule_id "f_dereference-$v-$r" $r $v \
            "tp: turbine::f_dereference_float_body $v $r"
    }

    proc f_dereference_float_body { v r } {
        # Get the TD from the reference
        set id [ get $r ]
        # When the TD has a value, copy the value
        copy_float no_stack $v $id
    }

    # When reference r is closed, copy its (string) value into v
    proc f_dereference_string { parent v r } {
        set rule_id [ rule_new ]
        rule $rule_id "f_dereference-$v-$r" $r $v \
            "tp: turbine::f_dereference_string_body $v $r"
    }
    proc f_dereference_string_body { v r } {
        # Get the TD from the reference
        set id [ get $r ]
        # When the TD has a value, copy the value
        copy_string no_stack $v $id
    }

    # When reference cr is closed, store d = (*cr)[i]
    # Blocks on cr
    # inputs: [ list cr i d ]
    #       cr is a reference to a container
    #       i is a literal int
    # outputs: ignored
    proc f_container_reference_lookup_literal { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "f_container_reference_lookup_literal-$cr" "$cr" "" \
            "tp: turbine::f_container_reference_lookup_literal_body $cr $i $d"

    }

    proc f_container_reference_lookup_literal_body { cr i d } {
        # When this procedure is run, cr should be set and
        # i should be the literal index
        set c [ get $cr ]
        adlb::container_reference $c $i $d
    }

    # When reference cr is closed, store d = (*cr)[i]
    # Blocks on cr and i
    # inputs: [ list cr i d ]
    #       cr is a reference to a container
    # outputs: ignored
    proc f_container_reference_lookup { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "f_container_reference_lookup-$cr" "$cr $i" "" \
            "tp: turbine::f_container_reference_lookup_body $cr $i $d"
    }

    proc f_container_reference_lookup_body { cr i d } {
        # When this procedure is run, cr and i should be set
        set c [ get $cr ]
        set t1 [ get $i ]
        adlb::container_reference $c $t1 $d
    }
    # When reference r on c[i] is closed, store c[i][j] = d
    # Blocks on r and j
    # inputs: [ list r j d ]
    # outputs: ignored
    proc f_container_reference_insert { parent outputs inputs } {
        set r [ lindex $inputs 0 ]
        # set c [ lindex $inputs 1 ]
        set j [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "f_container_reference_insert-$r" "$r $j" "" \
            "tp: turbine::f_container_reference_insert_body $r $j $d"
    }
    proc f_container_reference_insert_body { r j d } {
        # s: The subscripted container
        set c [ get $r ]
        set s [ get $j ]
        container_insert $c $s $d
    }


    # Insert c[i][j] = d
    proc f_container_nested_insert { c i j d } {

        set rule_id [ rule_new ]
        rule $rule_id "fcni" "$i $j" "" \
            "tp: f_container_nested_insert_body_1 $c $i $j $d"
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
            set rule_id [ rule_new ]
            rule $rule_id fcnib "$r" "" \
                "tp: container_nested_insert_body_2 $r $j $d"
        }
    }

    proc f_container_nested_insert_body_2 { r j d } {
        container_insert $r $j $d
    }

    proc imm_container_create_nested { r c i type } {
        debug "container_create_nested: $r $c\[$i\] $type"
        upvar 1 $r v
        allocate v integer
        __container_create_nested $v $c $i $type
    }

    proc __container_create_nested { r c i type } {
        debug "__container_create_nested: $r $c\[$i\] $type"
        if [ adlb::insert_atomic $c $i ] {
            # Member did not exist: create it and get reference
            allocate_container t $type
            adlb::insert $c $i $t
        }

        adlb::container_reference $c $i $r
    }

    proc f_container_create_nested { r c i type } {

        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r

        set rule_id [ rule_new ]
        rule $rule_id fccn "" "$i" \
               "tp: f_container_create_nested_body $tmp_r $c $i $type"
    }


    # Create container at c[i]
    # Set r, a reference TD on c[i]
    proc f_container_create_nested_body { r c i type } {

        debug "f_container_create_nested: $r $c\[$i\] $type"

        set s [ get $i ]
        __container_create_nested $r $c $s $type
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc container_reference_create_nested { r cr i type } {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r

        set rule_id [ rule_new ]
        rule $rule_id fcrcn "" "$cr" \
           "tp: container_reference_create_nested_body $tmp_r $cr $i $type"
    }

    proc f_container_reference_create_nested_body { r cr i type } {
        set c [ get $cr ]
        __container_create_nested $r $c $i $type
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc f_container_reference_create_nested { r cr i type } {
        upvar 1 $r v

        # Create reference
        data_new tmp_r
        create_integer $tmp_r
        set v $tmp_r

        set rule_id [ rule_new ]
        rule $rule_id fcrcn "" "$cr $i" \
           "tp: f_container_reference_create_nested_body $tmp_r $cr $i $type"
    }

    proc f_container_reference_create_nested_body { r cr i type } {
        set c [ get $cr ]
        set s [ get $i ]
        container_create_nested $r $c $s $type
    }

    variable container_branches

    # c: container
    # r: rule id
    proc container_branch_post { r c } {

        variable container_branches

        puts "container_branch_post: rule: $r container: $c"
        dict lappend container_branches $r $c
        adlb::slot_create $c
    }

    # r: rule id
    proc container_branch_complete { r } {

        variable container_branches

        puts "container_branch_complete: $r"
        set cL [ dict get $container_branches $r ]
        foreach c $cL {
            puts "container: $c"
            adlb::slot_drop $c
        }
        set container_branches [ dict remove $container_branches ]
    }

    # When container is closed, concatenate its keys in result
    # container: The container to read
    # result: An initialized string
    proc enumerate { stack result container } {
        set rule_id [ rule_new ]
        rule $rule_id "enumerate-$container" $container $result \
            "tp: enumerate_body $result $container"
    }

    proc enumerate_body { result container } {
        set s [ container_list $container ]
        set_string $result $s
    }

    # Sum all of the values in a container of integers
    # inputs: [ list c r ]
    # c: the container
    # r: the turbine id to store the sum into
    proc sum_integer { stack outputs inputs } {
        set container [ lindex $inputs 0 ]
        set result [ lindex $outputs 0 ]
        set rule_id [ rule_new ]
        rule $rule_id "sum-$rule_id" $container "" \
            "tp: sum_integer_body $stack $container $result 0 0"
    }

    proc sum_integer_body { stack container result accum next_index } {
        set keys [ container_list $container ]
        # TODO: could divide and conquer instead of
        #       doing linear search
        set n [ llength $keys ]
        set i $next_index
        while { $i < $n } {
            set key [ lindex $keys $i ]
            set turbine_id [ container_get $container $key ]

            if { [ adlb::exists $turbine_id ] } {
                # add to the sum
                set val [ get_integer $turbine_id ]
                #puts "C\[$i\] = $val"
                set accum [ expr $accum + $val ]
                incr i
            } else {
                # block until the next turbine id is finished,
                #   then continue running
                set rule_id [ rule_new ]
                rule $rule_id "sum-$rule_id" $turbine_id "" \
                    "tp: sum_integer_body $stack $container $result $accum $i"
                # return immediately without setting result
                return
            }
        }
        # If we get out of loop, we're done
        set_integer $result $accum
    }
}
