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

  proc string_from_blob { result input } {
    rule $input "string_from_blob_body $input $result" \
        name "sfb-$input-$result"
  }
  proc string_from_blob_body { input result } {
    set s [ retrieve_decr_blob_string $input ]
    store_string $result $s
  }

  proc floats_from_blob { result input } {
      rule $input "floats_from_blob_body $result $input" \
          name "floats_from_blob-$result"
  }
  proc floats_from_blob_body { result input } {
      log "floats_from_blob_body: result=<$result> input=<$input>"
      set s      [ blobutils_sizeof_float ]
      set L      [ adlb::retrieve_blob $input ]
      set p      [ blobutils_cast_int_to_dbl_ptr [ lindex $L 0 ] ]
      set length [ lindex $L 1 ]

      set n [ expr {$length / $s} ]
      for { set i 0 } { $i < $n } { incr i } {
          set d [ blobutils_get_float $p $i ]
          literal t float $d
          container_immediate_insert $result $i $t ref
      }
      adlb::refcount_incr $result w -1
      adlb::blob_free $input
      log "floats_from_blob_body: done"
  }

  # This is just in Fortran order for now
  # b: the blob
  # m: number of rows
  # n: number of columns
  proc matrix_from_blob_fortran { result inputs } {
      set b [ lindex $inputs 0 ]
      set m [ lindex $inputs 1 ]
      set n [ lindex $inputs 2 ]
      rule [ list $b $m $n ] \
          "matrix_from_blob_fortran_body $result $inputs" \
          name "matrix_from_blob-$result"
  }
  proc matrix_from_blob_fortran_body { result b m n } {
      log "matrix_from_blob_fortran_body: result=<$result> blob=<$b>"
      # Retrieve inputs
      set s       [ blobutils_sizeof_float ]
      set L       [ retrieve_decr_blob $b ]
      set p       [ blobutils_cast_int_to_dbl_ptr [ lindex $L 0 ] ]
      set m_value [ retrieve_integer $m ]
      set n_value [ retrieve_integer $n ]
      set length  [ lindex $L 1 ]

      # total = m x n
      # i is row index:       0..m-1
      # j is column index:    0..n-1
      # k is index into blob: 0..total-1
      # c[i] is row result[i]
      set total [ expr {$length / $s} ]
      if { $total != $m_value * $n_value } {
          error "matrix_from_blob: blob size $total != $m_value x $n_value"
      }
      for { set i 0 } { $i < $m_value } { incr i } {
          set c($i) [ allocate_container integer ref ]
          container_immediate_insert $result $i $c($i) ref
      }
      for { set k 0 } { $k < $total } { incr k } {
          set d [ blobutils_get_float $p $k ]
          literal t float $d
          set i [ expr {$k % $m_value} ]
          set j [ expr {$k / $m_value} ]
          container_immediate_insert $c($i) $j $t ref
      }
      # Close rows
      for { set i 0 } { $i < $m_value } { incr i } {
          adlb::refcount_incr $c($i) w -1
      }
      # Close result
      adlb::refcount_incr $result w -1
      # Release cached blob
      adlb::blob_free $b
      log "matrix_from_blob_fortran_body: done"
  }

  # Container must be indexed from 0,N-1
  proc blob_from_floats { result input } {
    rule $input  "blob_from_floats_body $input $result" \
        name "blob_from_floats-$result"
  }
  proc blob_from_floats_body { container result } {

      set N  [ adlb::container_size $container ]
      c::log "blob_from_floats_body start"
      complete_container $container \
          "blob_from_floats_store $result $container $N"
  }
  # This is called when every entry in container is set
  proc blob_from_floats_store { result container N } {
    set A [ list ]
    for { set i 0 } { $i < $N } { incr i } {
      set td [ container_lookup $container $i ]
      set v  [ retrieve_float $td ]
      lappend A $v
    }
    adlb::store_blob_floats $result $A
    read_refcount_decr $container
  }

  # Container must be indexed from 0,N-1
  proc blob_from_ints { result input } {
    rule $input "blob_from_ints_body $input $result" \
        name "blob_from_ints-$result"
  }
  proc blob_from_ints_body { container result } {

      set N  [ adlb::container_size $container ]
      c::log "blob_from_ints_body start"
      complete_container $container \
          "blob_from_ints_store $result $container $N"
  }
  # This is called when every entry in container is set
  proc blob_from_ints_store { result container N } {
    set A [ list ]
    for { set i 0 } { $i < $N } { incr i } {
      set td [ container_lookup $container $i ]
      set v  [ retrieve_integer $td ]
      lappend A $v
    }
    adlb::store_blob_ints $result $A
    read_refcount_decr $container
  }

  proc blob_zeroes_float { N } {

      log "blob_zeroes_float($N)"

      set length [ expr $N * [blobutils_sizeof_float] ]
      set p [ blobutils_malloc $length ]

      blobutils_zeroes_float [ blobutils_cast_to_dbl_ptr $p ] $N

      return [ list [ blobutils_cast_to_int $p ] $length ]
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
  proc complete_container { A action } {
      set n [ adlb::container_size $A ]
      log "complete_container: <$A> size: $n"
      complete_container_continue $A $action 0 $n
  }
  proc complete_container_continue { A action i n } {
      log "complete_container_continue: <$A> $i/$n"
      if { $i < $n } {
          set x [ container_lookup $A $i ]
          if { $x == 0 } {
              error "complete_container: <$A>\[$i\]=<0>"
          }
          rule [ list $x ] \
              "complete_container_continue_body $A {$action} $i $n" \
              name "complete_container_continue-$A"
      } else {
          eval $action
      }
  }
  proc complete_container_continue_body { A action i n } {
      complete_container_continue $A $action [ incr i ] $n
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
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
