
proc func { A } {

    set C [ adlb::enumerate $A members all 0 ]

    set N [ llength $C ]

    set A [ new_string_array ]
    string_array_string_array_create $A $N
    for { set i 1 } { $i <= $N } { incr i } {
        set td [ lindex $C [ expr $i - 1 ] ]
        set s [ retrieve $td ]
        string_array_string_array_set $A $i $s
    }

    set p [ blobutils_malloc [blobutils_sizeof_float]]
    set d [ blobutils_cast_to_dbl_ptr $p ]

    FortFuncs_func 3 $A $d

    set v [ blobutils_get_float $d 0 ]
    blobutils_free $p

    return $v
}
