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

namespace eval turbine {

    namespace export blob_fmt blob_debug blob_debug_ints

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

  proc blob_size { blob_val } {
    return [ lindex $blob_val 1 ]
  }

  proc blob_null { result input } {
      store_blob $result 0 0
  }

  proc blob_from_string { result input } {
    rule $input "blob_from_string_body $input $result" \
        name "bfs-$input-$result"
  }
  proc blob_from_string_body { input result } {
    set t [ retrieve_decr_string $input ]
    store_blob_string $result $t
  }

  proc blob2string { result input } {
    rule $input "blob2string_body $input $result" \
        name "sfb-$input-$result"
  }
  proc blob2string_body { input result } {
    set s [ retrieve_decr_blob_string $input ]
    store_string $result $s
  }

  proc blob2floats_impl { blob } {
    set s       [ blobutils_sizeof_float ]
    set p      [ blobutils_cast_long_to_dbl_ptr [ lindex $blob 0 ] ]
    set length [ lindex $blob 1 ]

    set n [ expr {$length / $s} ]
    set result [ dict create ]
    for { set i 0 } { $i < $n } { incr i } {
      dict append result $i [ blobutils_get_float $p $i ]
    }
    return $result
  }

  # This is just in Fortran order (column-major) for now
  # b: the blob
  # m: number of rows
  # n: number of columns
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

  proc blob_from_floats { out in } {
    set floats [ lindex $in 0 ]
    set blob [ lindex $out 0 ]
    rule $floats "blob_from_floats_body $blob $floats"
  }

  proc blob_from_floats_body { blob floats } {
    set floats_val [ adlb::retrieve $floats container ]
    set blob_val [ blob_from_floats_impl $floats_val ]
   
    store_blob $blob $blob_val
    adlb::local_blob_free $blob_val
  }

  proc blob_from_floats_impl { kv_dict } {
    return [ adlb::blob_from_float_list [ sorted_dict_values $kv_dict ] ]
  }

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

  proc blob_zeroes_float { N } {

      log "blob_zeroes_float($N)"

      set length [ expr $N * [blobutils_sizeof_float] ]
      set p [ blobutils_malloc $length ]

      blobutils_zeroes_float [ blobutils_cast_to_dbl_ptr $p ] $N

      return [ list [ blobutils_cast_to_long $p ] $length ]
  }

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

  proc blob_debug_ints { ptr n } {
      puts "blob_debug_ints: $ptr $n"
      for { set i 0 } { $i < $n } { incr i } {
          set v [ blobutils_get_int $ptr $i ]
          puts "  $i: $v"
      }
  }

    proc blob_fmt { b } {
        return [ format "( <%i> pointer=%X length=%i )" \
                     [ lindex $b 2 ] [ lindex $b 0 ] [ lindex $b 1 ] ]
    }

    proc blob_debug { b } {
        puts [ blob_fmt $b ]
    }

    # Input:  L: a Tcl dict of integer->integer indexed from 0
    # Output:    a SWIG pointer (double*) to a fresh double array
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

    # Input:  L: a Tcl dict of integer->string indexed from 0
    # Output:    a SWIG pointer (char**) to the C strings
    proc blob_strings_to_char_ptr_ptr { d } { 
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
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
