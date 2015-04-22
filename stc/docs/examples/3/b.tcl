
namespace eval b {
    # v is formatted as a Turbine blob a list of [ pointer length ]
    # The pointer is a simple Tcl integer
    # The length is the byte length
    proc b_tcl { v } {

        # Unpack the list
        set ptr [ lindex $v 0 ]
        set len [ lindex $v 1 ]

        # Get the number of numbers to sum
        set count [ expr $len / [ blobutils_sizeof_float ] ]

        # Convert the pointer number to a SWIG pointer
        set ptr [ blobutils_cast_int_to_dbl_ptr $ptr ]

        # Call the C function
        set s [ b $ptr $count ]

        # Pack result as a Turbine blob and return it
        set r [ blobutils_cast_to_int $s ]
        return [ list $r 8 ]
    }
}
