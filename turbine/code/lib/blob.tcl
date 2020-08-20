
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
# Turbine builtin functions for blob manipulation

# BLOB.TCL
# Turbine implementations for blob.swift

# DOCSECTION(Tcl convenience functions)
# DOCN(These functions operate on Tcl blobs.)

namespace eval turbine {

    namespace export blob_fmt blob_debug blob_debug_ints

    # DOCD(blob_size_async out blob,
    #      Turbine rule to obtain the size of TD +blob+ in TD +out+)
    proc blob_size_async { out blob } {
        rule "$blob" "blob_size_body $out $blob" \
            name "blob_size-$out-$blob"
    }
    proc blob_size_body { out blob } {
        set blob_val [ retrieve_decr_blob $blob ]
        set sz [ blob_size $blob_val ]
        store_integer $out $sz
        adlb::blob_free $blob
    }

  # DOCD(blob_size blob,
  #      Obtain the size of blob)
  proc blob_size { blob_val } {
    return [ lindex $blob_val 1 ]
  }

  # DOCD(blob_null blob,
  #      Store +NULL+ in the blob)
  proc blob_null { result input } {
      store_blob $result 0 0
  }

  # DOCD(string2blob,
  #      Turbine rule to translate TD blob +input+ into TD string +result+)
  proc string2blob { result input } {
    rule $input "string2blob_body $input $result" \
        name "string2blob-$input-$result"
  }
  proc string2blob_body { input result } {
    set t [ retrieve_decr_string $input ]
    store_blob_string $result $t
  }

  # DOCD(blob2string,
  #      Turbine rule to translate TD string +input+ in TD blob +result+)
  proc blob2string { result input } {
    rule $input "blob2string_body $input $result" \
        name "sfb-$input-$result"
  }
  proc blob2string_body { input result } {
    set s [ retrieve_decr_blob_string $input ]
    store_string $result $s
  }

  # DOCD(blob2floats,
  #      `Obtain doubles from blob, return as dictionary
  #       mapping integer index to double')
  proc blob2floats_impl { blob } {
    set s      [ blobutils_sizeof_float ]
    set p      [ blobutils_cast_long_to_dbl_ptr [ lindex $blob 0 ] ]
    set length [ lindex $blob 1 ]

    set n [ expr {$length / $s} ]
    set result [ dict create ]
    for { set i 0 } { $i < $n } { incr i } {
      dict append result $i [ blobutils_get_float $p $i ]
    }
    return $result
  }

  # DOCD(matrix_from_blob_fortran_impl blob m n,
  #      `Obtain doubles from blob, return as dictionary
  #       mapping integer indices to double in Fortran order,
  #       +m+ is the number of rows, +n+ is the number of columns')
  # This is just in Fortran order (column-major) for now
  proc matrix_from_blob_fortran_impl { L m n } {
    set s       [ blobutils_sizeof_float ]
    set p       [ blobutils_cast_long_to_dbl_ptr [ lindex $L 0 ] ]
    set length  [ lindex $L 1 ]

    set result  [ dict create ]

    # total = m x n
    # i is row index:       0..m-1
    # j is column index:    0..n-1
    # k is index into blob: 0..total-1
    # c[i] is row result[i]
    set total [ expr {$length / $s} ]
    if { $total != $m * $n } {
        error "matrix_from_blob: blob size $total != $m x $n"
    }
    for { set i 0 } { $i < $m } { incr i } {
      set row [ dict create ]

      for { set j 0 } { $j < $n } { incr j } {
        set k [ expr { $j * $m + $i} ]
        dict append row $j [ blobutils_get_float $p $k ]
      }
      dict append result $i $row
    }
    return $result
  }

  proc sorted_dict_values { kv_dict } {
    set N [ dict size $kv_dict ]
    set A [ list ]
    for { set i 0 } { $i < $N } { incr i } {
      set v [ dict get $kv_dict $i ]
      lappend A $v
    }
    return $A
  }

  # DOCD(floats2blob out in, Store Tcl floats from dict into blob)
  proc floats2blob { out in } {
    set floats [ lindex $in 0 ]
    set blob [ lindex $out 0 ]
    rule $floats "floats2blob_body $blob $floats"
  }
  proc floats2blob_body { blob floats } {
    set floats_val [ adlb::retrieve $floats container ]
    set blob_val [ floats2blob_impl $floats_val ]

    store_blob $blob $blob_val
    adlb::local_blob_free $blob_val
  }
  proc floats2blob_impl { kv_dict } {
    return [ adlb::blob_from_float_list [ sorted_dict_values $kv_dict ] ]
  }

  # DOCD(floats2blob out in, Store Tcl ints from dict into blob)
  proc ints2blob { out in } {
    set ints [ lindex $in 0 ]
    set blob [ lindex $out 0 ]
    rule $ints "ints2blob_body $blob $ints"
  }
  proc ints2blob_body { blob ints } {
    set ints_val [ adlb::retrieve $ints container ]
    set blob_val [ ints2blob_impl $ints_val ]

    store_blob $blob $blob_val
    adlb::local_blob_free $blob_val
  }
  proc ints2blob_impl { kv_dict } {
    return [ adlb::blob_from_int_list [ sorted_dict_values $kv_dict ] ]
  }

  # DOCD(floats2blob n, Store +n+ zeroes into new blob)
  proc blob_zeroes_float { N } {

      log "blob_zeroes_float($N)"

      set length [ expr $N * [blobutils_sizeof_float] ]
      set p [ blobutils_malloc $length ]

      blobutils_zeroes_float [ blobutils_cast_to_dbl_ptr $p ] $N

      return [ list [ blobutils_cast_to_long $p ] $length ]
  }

  # DOCD(turbine_run_output_blob dummy blob,
  #      `Store blob as +turbine_run()+ result, +dummy+ is unused')
  proc turbine_run_output_blob { outputs b } {

      rule [ list $b ] "turbine_run_output_blob_body $b" \
          target 0 type $turbine::CONTROL
  }
  proc turbine_run_output_blob_body { b } {

      global turbine_run_output

      if { ! [ info exists turbine_run_output ] } {
          error "Not running under turbine_run()!"
      }

      set b_value [ retrieve_blob $b ]
      set ptr     [ lindex $b_value 0 ]
      set length  [ lindex $b_value 1 ]
      blobutils_turbine_run_output_blob $turbine_run_output \
                                        $ptr $length
      free_blob $b
  }

  # Assumes A is closed
  # Deprecated: number types now stored inline in container
  proc complete_container { A action } {
      eval $action
  }

  # DOCD(blob_debug_ints blob, Print blob of ints)
  proc blob_debug_ints { b } {
      lassign $b ptr n
      puts "blob_debug_ints: $ptr $n"
      for { set i 0 } { $i < $n } { incr i } {
          set v [ blobutils_get_int $ptr $i ]
          puts "  $i: $v"
      }
  }

    # DOCD(blob_fmt blob,
    #      Return printable blob metadata
    #      (TD, pointer, length) as string)
    proc blob_fmt { b } {
        return [ format "( <%i> pointer=%X length=%i )" \
                     [ lindex $b 2 ] [ lindex $b 0 ] [ lindex $b 1 ] ]
    }

    # DOCD(blob_fmt blob,
    #      Print blob metadata (TD, pointer, length) via +blob_fmt+)
    proc blob_debug { b } {
        puts [ blob_fmt $b ]
    }

    # DOCD(blob_dict_to_int_array,
    #      `Input:  L: a Tcl dict of integer->integer indexed from 0. +
    #       Output:    a SWIG pointer (+int*+) to a fresh array of +int+.')
    proc blob_dict_to_int_array { d } {
        set L [ dict size $d ]
        set bytes [ expr $L * [ blobutils_sizeof_int ] ]
        set ptr [ blobutils_malloc $bytes ]
        set ptr [ blobutils_cast_to_int_ptr $ptr ]
        for { set i 0 } { $i < $L } { incr i } {
            set v [ dict get $d $i ]
            blobutils_set_int $ptr $i $v
        }
        return $ptr
    }

    # DOCD(blob_string_list_to_char_ptr_ptr,
    #      `Convert Tcl list of strings to +char**+. +
    #       Helpful for passing data into C-style argc/argv interfaces.')
    proc blob_string_list_to_char_ptr_ptr { L } {
        set D [ list2dict $L ]
        return [ blob_string_dict_to_char_ptr_ptr $D ]
    }

    # DOCD(blob_string_dict_to_char_ptr_ptr d,
    #      `Input: A Tcl dict of integer->string indexed from 0. +
    #       Output: A SWIG pointer (+char**+) to the C strings. +
    #       Helpful for passing data into C-style argc/argv interfaces.')
    proc blob_string_dict_to_char_ptr_ptr { d } {
        set argc [ dict size $d ]
        # Allocate array of char*
        set bytes [ expr $argc * [blobutils_sizeof_ptr] ]
        # This is a void*
        set alloc [ blobutils_malloc $bytes ]
        # This is a char**
        set charss [ blobutils_cast_to_char_ptrptr $alloc ]

        # Fill in argv...
        # This is a void**
        set v [ blobutils_cast_to_ptrptr $alloc ]
        # Set arguments...
        for { set i 0 } { $i < $argc } { incr i } {
            set s [ dict get $d $i ]
            set p [ blobutils_cast_string_to_ptr $s ]
            blobutils_set_ptr $v $i $p
        }
        return $charss
    }

    # DOCD(`blob_strings_to_char_ptr_ptr d',
    #      Deprecated alias for +blob_string_dict_to_char_ptr_ptr+.)
    proc blob_strings_to_char_ptr_ptr { d } {
        return [ blob_string_dict_to_char_ptr_ptr $d ]
    }

    # DOCD(blob_string_list_to_argv program L argv_name,
    #      `Convert Tcl list of strings +L+ to +char\*\*+. +
    #       Helpful for passing data into C-style argc/argv interfaces. +
    #       Puts result (+char**+) in +argv_name+. +
    #       +program+: Program name as string (i.e., argv[0]). +
    #       +L+: list of strings (i.e., argv[1]...argv[argc-1]). +
    #       Returns +argc+.')
    proc blob_string_list_to_argv { program L argv_name } {
        upvar $argv_name argv
        set D [ list2dict [ list $program {*}$L ] ]
        set argc [ dict size $D ]
        set argv [ blob_string_dict_to_char_ptr_ptr $D ]
        return $argc
    }

    # DOCD(blob_string_dict_to_char_ppp d,
    #      `Input: A Tcl dict of int->int->string indexed from 0. +
    #       Output: A SWIG pointer (+char***+) to the C strings. +
    #       Helpful for passing data into C-style multi argc/argv interfaces.')
    proc blob_string_dict_to_char_ppp { d } {
        set v [ dict size $d ]
        # Allocate array of char**
        set bytes [ expr $v * [blobutils_sizeof_ptr] ]
        # This is a void*
        set alloc [ blobutils_malloc $bytes ]
		# This is a void**
		set alloc** [ blobutils_cast_to_ptrptr $alloc ]
        # This is a char***
        set charppp [ blobutils_cast_to_char_ppp $alloc ]
		dict for { k v } $d {
			set p [ blob_string_dict_to_char_ptr_ptr $v ]
			set p [ blobutils_cast_char_ptrptr_to_ptr $p ]
			blobutils_set_ptr ${alloc**} $k $p
		}
		return $charppp
	}
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
