
namespace eval mvm {

    proc mvm { A x n } {

        set A_ptr [ lindex $A 0 ]
        set x_ptr [ lindex $x 0 ]

        set t         [ blobutils_cast_lli_to_ptr $A_ptr ]
        set A_dbl_ptr [ blobutils_cast_to_dbl_ptr $t ]

        set t         [ blobutils_cast_lli_to_ptr $x_ptr ]
        set x_dbl_ptr [ blobutils_cast_to_dbl_ptr $t ]


        set length [ expr $n * [blobutils_sizeof_float] ]
        set y_ptr [ blobutils_malloc $length ]
        set y_dbl_ptr [ blobutils_cast_to_dbl_ptr $y_ptr ]
        FortFuncs_MVM $A_dbl_ptr $x_dbl_ptr $y_dbl_ptr $n

        set y_int [ blobutils_cast_to_lli $y_ptr ]
        return [ list $y_int $length ]
    }
}
