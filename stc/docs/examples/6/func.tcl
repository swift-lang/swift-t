
proc func { A } {

    show A

    set N [ dict size $A ]

    show N

    set S [ new_string_array ]
    string_array_string_array_create $S $N
    for { set i 0 } { $i < $N } { incr i } {
        # Fortran index: i+1
        set j [ expr $i + 1 ]
        # Pull out string i as t
        set t [ dict get $A $i ]
        string_array_string_array_set $S $j $t
    }

    set p [ blobutils_malloc [blobutils_sizeof_float]]
    set d [ blobutils_cast_to_dbl_ptr $p ]

    FortFuncs_func 3 $S $d

    set v [ blobutils_get_float $d 0 ]
    blobutils_free $p

    return $v
}
