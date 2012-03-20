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
        container_insert $c $t1 $d 1
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
        set t1 [ get_integer $i ]
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
        # adlb::slot_create $c
        container_insert $c $i $d 0
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
    proc f_cref_lookup_literal { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "f_cref_lookup_literal-$cr" "$cr" "" \
            "tp: turbine::f_cref_lookup_literal_body $cr $i $d"

    }

    proc f_cref_lookup_literal_body { cr i d } {
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
    proc f_cref_lookup { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set d [ lindex $inputs 2 ]
        set rule_id [ rule_new ]
        rule $rule_id "f_cref_lookup-$cr" "$cr $i" "" \
            "tp: turbine::f_cref_lookup_body $cr $i $d"
    }

    proc f_cref_lookup_body { cr i d } {
        # When this procedure is run, cr and i should be set
        set c [ get $cr ]
        set t1 [ get $i ]
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
        set rule_id [ rule_new ]
        rule $rule_id "f_cref_insert-$r-$j-$d-$oc" "$r $j" "" \
            "tp: turbine::f_cref_insert_body $r $j $d $oc"
    }
    proc f_cref_insert_body { r j d oc } {
        # s: The subscripted container
        set c [ get_integer $r ]
        set s [ get_integer $j ]
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
        set rule_id [ rule_new ]
        rule $rule_id "cref_insert-$cr-$j-$d-$oc" "$cr" "" \
            "tp: turbine::cref_insert_body $cr $j $d $oc"
    }
    proc cref_insert_body { cr j d oc } {
        set c [ get_integer $cr ]
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
        set rule_id [ rule_new ]
        rule $rule_id "cref_deref_insert-$cr-$j-$dr-$oc" "$cr $dr" "" \
            "tp: turbine::cref_deref_insert_body $cr $j $dr $oc"
    }
    proc cref_deref_insert_body { cr j dr oc } {
        set c [ get_integer $cr ]
        set d [ get $dr ]
        container_insert $c $j $d
        adlb::slot_drop $oc
    }
    
    proc cref_f_deref_insert { parent outputs inputs } {
        set cr [ lindex $inputs 0 ]
        set j [ lindex $inputs 1 ]
        set dr [ lindex $inputs 2 ]
        set oc [ lindex $inputs 3 ]
        adlb::slot_create $oc
        set rule_id [ rule_new ]
        rule $rule_id "cref_f_deref_insert-$cr-$j-$dr-$oc" "$cr $j $dr" "" \
            "tp: turbine::cref_f_deref_insert_body $cr $j $dr $oc"
    }
    proc cref_f_deref_insert_body { cr j dr oc } {
        set c [ get_integer $cr ]
        set d [ get $dr ]
        set jval [ get_integer $j ]
        container_insert $c $jval $d
        adlb::slot_drop $oc
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

    proc container_create_nested { c i type } {
      debug "container_create_nested: $c\[$i\] $type"
      if [ adlb::insert_atomic $c $i ] {
        debug "$c\[$i\] doesn't exist, creating" 
        # Member did not exist: create it and get reference
        allocate_container t $type
        adlb::insert $c $i $t
        # setup rule to close when outer container closes
        set rule_id [ rule_new ]
        rule $rule_id "autoclose-$c-$t" "$c" "" \
               "tp: adlb::slot_drop $t"
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

        set rule_id [ rule_new ]
        rule $rule_id fccn "" "$i" \
               "tp: f_container_create_nested_body $tmp_r $c $i $type"
    }


    # Create container at c[i]
    # Set r, a reference TD on c[i]
    proc f_container_create_nested_body { r c i type } {

        debug "f_container_create_nested: $r $c\[$i\] $type"

        set s [ get $i ]
        set res [ container_create_nested $c $s $type ]
        set_integer $r $res
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc cref_create_nested { r cr i type } {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r

        set rule_id [ rule_new ]
        rule $rule_id fcrcn "" "$cr" \
           "tp: cref_create_nested_body $tmp_r $cr $i $type"
    }

    proc cref_create_nested_body { r cr i type } {
        set c [ get $cr ]
        set res [ container_create_nested $c $i $type ]
        set_integer $r $res
    }

    # Create container at c[i]
    # Set r, a reference TD on (cr*)[i]
    proc f_cref_create_nested { r cr i type } {
        upvar 1 $r v

        # Create reference
        allocate tmp_r integer
        set v $tmp_r

        set rule_id [ rule_new ]
        rule $rule_id fcrcn "" "$cr $i" \
           "tp: f_cref_create_nested_body $tmp_r $cr $i $type"
    }

    proc f_cref_create_nested_body { r cr i type } {
        set c [ get $cr ]
        set s [ get $i ]
        set res [ container_create_nested $c $s $type ]
        set_integer $r $res
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
    proc sum_integer { stack result inputs } {
        set container [ lindex $inputs 0 ]
        set rule_id [ rule_new ]
        rule $rule_id "sum-$rule_id" $container "" \
            "tp: sum_integer_body $stack $container $result 0 0"
    }

    proc sum_integer_body { stack container result accum next_index } {
        debug "sum_integer $container => $result"
        set keys [ container_list $container ]
        # TODO: could divide and conquer instead of
        #       doing linear search
        set n [ llength $keys ]
        set i $next_index
        while { $i < $n } {
            set key [ lindex $keys $i ]
            set turbine_id [ container_get $container $key ]
            #puts "turbine_id: $turbine_id"
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
    
    
    # calculate mean of an array of floats
    proc avg_float { parent result container } {
        set NULL 0
        stats_impl $container $NULL $result $NULL $NULL $NULL $NULL
    }
    
    # calculate mean of an array of floats
    proc std_float { parent result container } {
        set NULL 0
        stats_impl $container $NULL $NULL $NULL $result $NULL $NULL
    }

    proc stats_float { parent outputs container } {
        set NULL 0
        set mean [ lindex $outputs 0 ]
        set std [ lindex $outputs 1 ]
        stats_impl $container $NULL $mean $NULL $std $NULL $NULL
    }

    proc stats_impl { container sum_out mean_out samp_std_out pop_std_out\
                    max_out min_out } {
        set rule_id [ rule_new ]
        rule $rule_id "avg-$rule_id" $container "" \
            "tp: stats_body $container $sum_out $mean_out $samp_std_out\
             $pop_std_out $max_out $min_out 0.0 0.0 0.0 0.0 0.0 0"
    }

    # Calculate mean, standard deviation, max, min for array of float or int
    proc stats_body { container sum_out mean_out samp_std_out pop_std_out\
                    max_out min_out sum_accum mean_accum std_accum min_accum\
                    max_accum next_index } {
        debug "stats_body $container"
        set keys [ container_list $container ]
        # TODO: could divide and conquer instead of doing linear search
        set n [ llength $keys ]
        set i $next_index
        while { $i < $n } {
            set key [ lindex $keys $i ]
            set turbine_id [ container_get $container $key ]
            #puts "turbine_id: $turbine_id"
            if { [ adlb::exists $turbine_id ] } {
                # retrieve value and make sure its floating point
                # so we don't get surprised by integer division
                set x [ get $turbine_id ]
                set x [ expr double($x) ]
                puts "c\[$key\] = $x"
                if { $sum_out != 0 } {
                    # avoid potential of overflow
                    set sum_accum [ expr $sum_account $x ]
                }
                set min_accum [ expr min($min_accum, $x) ]
                set max_accum [ expr max($max_accum, $x) ]
                # Note: use knuth's online algorithm for mean and std
                set delta [ expr $x - $mean_accum ]
                set mean_accum [ expr $mean_accum + ( $delta / ($i + 1) ) ]
                puts "mean_accum = $mean_accum"
                set std_accum [ expr $std_accum + $delta*($x - $mean_accum)]
                incr i
            } else {
                # block until the next turbine id is finished,
                #   then continue running
                set rule_id [ rule_new ]
                rule $rule_id "sum-$rule_id" $turbine_id "" \
                    "tp: sum_integer_body $stack $container $sum_out 
                         $mean_out \
                         $samp_std_out $pop_std_out $max_out $min_out \
                         $sum_accum $mean_accum $std_accum \
                         $min_accum $max_accum $next_index"
                # return immediately without setting result
                return
            }
        }
        # If we get out of loop, we're done
        if { $mean_out != 0 } {
            set_float $mean_out $mean_accum
        }
        
        if { $min_out != 0 } {
            set_float $min_out $min_accum
        }
        
        if { $max_out != 0 } {
            set_float $max_out $max_accum
        }

        if { $samp_std_out != 0 } {
            set_float $samp_std_out [ expr sqrt($std_accum / ($n - 1)) ]
        }

        if { $pop_std_out != 0 } {
            set_float $pop_std_out [ expr sqrt($std_accum / $n) ]
        }
    }
}
