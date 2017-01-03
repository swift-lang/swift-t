#/usr/bin/env turbine -n 1

# TURBINE-READ-DOUBLES

# Reads a file of doubles, prints them to stdout

# Usage: turbine-read-doubles.tcl <input>

package require turbine

if { $argc < 1 } {
    puts "requires input file name!"
    exit 1
}

set input [ lindex $argv 0 ]

set blob [ new_turbine_blob ]

blobutils_read $input $blob

set ptr    [ turbine_blob_pointer_get $blob ]
set bytes  [ turbine_blob_length_get  $blob ]
set length [ expr $bytes / [ blobutils_sizeof_float ] ]
set dptr   [ blobutils_cast_to_dbl_ptr $ptr ]

for { set i 0 } { $i < $length } { incr i } {
    set v [ blobutils_get_float $dptr $i ]
    puts [ format "%0.4f" $v ]
}
