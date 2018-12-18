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

# Aggregate numerical operations on containers

namespace eval turbine {

    # Sum all of the values in a container of integers
    # inputs: [ list c r ]
    # c: the container
    # r: the turbine id to store the sum into
    proc sum_integer { result inputs } {
        set container [ lindex $inputs 0 ]

        rule $container \
            "sum_integer_body $container $result" \
            name "sum-$result-$container" 
    }

    proc sum_integer_body { container result } {
        # TODO: could divide and conquer instead of doing linear search
        set CHUNK_SIZE 65536
        set accum 0
        set pos 0
        set maybe_more 1

        while { $maybe_more } {
          set members [ adlb::enumerate $container members $CHUNK_SIZE $pos ]
          set members_len [ llength $members ]
          set maybe_more [ expr $members_len == $CHUNK_SIZE ]
          incr pos $members_len

          # puts "members of $container $i $this_chunk_size : $members"
          foreach val $members {
            # add to the sum
            set accum [ expr {$accum + $val} ]
          }
        }
        # If we get out of loop, we're done
        store_integer $result $accum
        read_refcount_decr $container
    }

    # Sum all of the values in a container of floats
    # inputs: [ list c r ]
    # c: the container
    # r: the turbine id to store the sum into
    proc sum_float { result inputs } {
        set container [ lindex $inputs 0 ]

        rule $container \
            "sum_float_body $container $result" \
            name "sum-$result-$container" 
    }

    proc sum_float_body { container result } {
        # TODO: could divide and conquer instead of doing linear search
        set CHUNK_SIZE 65536
        set accum 0
        set pos 0
        set maybe_more 1

        while { $maybe_more } {
          set members [ adlb::enumerate $container members $CHUNK_SIZE $pos ]
          set members_len [ llength $members ]
          set maybe_more [ expr $members_len == $CHUNK_SIZE ]
          incr pos $members_len

          # puts "members of $container $i $this_chunk_size : $members"
          foreach val $members {
            # add to the sum
            set accum [ expr {$accum + $val} ]
          }
        }
        # If we get out of loop, we're done
        store_float $result $accum
        read_refcount_decr $container
    }

    # calculate mean of an array of floats or ints
    proc avg { result container } {
        set NULL 0
        stats_impl $container $NULL $NULL $result $NULL $NULL $NULL $NULL $NULL
    }

    # calculate std dev of an array of floats or ints
    proc std { result container } {
        set NULL 0
        stats_impl $container $NULL $NULL $NULL $NULL $NULL $result $NULL $NULL
    }

    proc stats { outputs container } {
        set NULL 0
        set mean [ lindex $outputs 0 ]
        set std [ lindex $outputs 1 ]
        stats_impl $container $NULL $NULL $mean $NULL $NULL $std $NULL $NULL
    }

    proc statagg { outputs container } {
        set NULL 0
        set n [ lindex $outputs 0 ]
        set mean [ lindex $outputs 1 ]
        set M2 [ lindex $outputs 2 ]
        stats_impl $container $n $NULL $mean $M2 $NULL $NULL $NULL $NULL
    }

    proc stats_impl { container n_out sum_out mean_out M2_out \
                    samp_std_out pop_std_out\
                    max_out min_out } {
        rule $container \
            "stats_body $container $n_out $sum_out $mean_out $M2_out \
             $samp_std_out $pop_std_out $max_out $min_out" \
            name "stats-body-$container" 
    }

    # Calculate mean, standard deviation, max, min for array of float or int
    proc stats_body { container n_out sum_out mean_out M2_out \
                samp_std_out pop_std_out max_out min_out } {
      set sum_accum 0.0
      set mean_accum 0.0 
      set M2_accum 0.0
      set min_accum NOMIN
      set max_accum NOMAX

      set CHUNK_SIZE 65536
      set n [ adlb::enumerate $container count all 0 ]
      
      # Store n immediately
      if { $n_out != 0 } {
        store_integer $n_out $n
      }

      set i 0
      while { $i < $n } {
        set last_chunk [ expr { $i + $CHUNK_SIZE >= $n } ]

        if { $last_chunk } {
          set this_chunk_size [ expr {$n - $i} ]
          set read_decr 1
        } else {
          set this_chunk_size $CHUNK_SIZE
          set read_decr 0
        }

        set vals [ adlb::enumerate $container members $this_chunk_size $i $read_decr ]

        foreach x $vals {
          # Make sure it's floating point so we don't get surprised by
          # integer division
          set x [ expr {double($x)} ]
          if { $sum_out != 0 } {
            # avoid potential of overflow
            set sum_accum [ expr {$sum_accum + $x} ]
          }
          if { $min_accum == {NOMIN} } {
            set min_accum $x
          } else {
            set min_accum [ expr {min($min_accum, $x)} ]
          }
          if { $max_accum == {NOMAX} } {
            set max_accum $x
          } else {
            set max_accum [ expr {max($max_accum, $x)} ]
          }
          # Note: use knuth's online algorithm for mean and std
          set delta [ expr {$x - $mean_accum} ]
          set mean_accum [ expr {$mean_accum + ( $delta / ($i + 1) )} ]
          set M2_accum [ expr {$M2_accum + $delta*($x - $mean_accum)} ]
          incr i
        }
      }
      
      if { $n == 0 } {
        # Handle corner case of empty container correctly
        read_refcount_decr $container
      }

      # We're done - store results
      if { $sum_out != 0 } {
        store_float $sum_out $sum_accum
      }

      if { $mean_out != 0 } {
        if { $n == 0 } {
          error "calculating mean of empty array <$container>"
        }
        store_float $mean_out $mean_accum
      }

      if { $min_out != 0 } {
        if { $n == 0 } {
          error "calculating min of empty array <$container>"
        }
        store_float $min_out $min_accum
      }

      if { $max_out != 0 } {
        if { $n == 0 } {
          error "calculating max of empty array <$container>"
        }
        store_float $max_out $max_accum
      }

      if { $M2_out != 0 } {
        store_float $M2_out $M2_accum
      }

      if { $samp_std_out != 0 } {
        if { $n == 0 } {
          error "calculating stddev of empty array <$container>"
        }
        store_float $samp_std_out [ expr {sqrt($M2_accum / ($n - 1))} ]
      }

      if { $pop_std_out != 0 } {
        if { $n == 0 } {
          error "calculating stddev of empty array <$container>"
        }
        store_float $pop_std_out [ expr {sqrt($M2_accum / $n)} ]
      }
    }


  # take a container of PartialStats and summarize them
  # outputs are mean, stddev
  # note: we return population std dev
  proc stat_combine { outputs container } {
    set n_out [ lindex $outputs 0 ]
    set mean_out [ lindex $outputs 1 ]
    set std_out [ lindex $outputs 2 ]
    rule $container "stat_combine_body $container $n_out $mean_out $std_out" \
        name "stats-combine-$container" 
  }
  
  # Setup reduction tree to calculate statistics
  # container: closed container
  proc stat_combine_body { container n_out mean_out std_out } {

    # TODO: don't really need keys, but enumerate doesn't support
    # acquiring references
    set contents [ adlb::acquire_ref $container container 1 1 ]
    set structs [ dict values $contents ]
    set n [ llength $structs ]
    
    if { $n == 0 } {
      error "mean and standard deviation not defined for sample size 0"
    }
    
    # Set up a k-way reduction tree
    set k 16

    # Number of internal nodes in reduce tree
    set reduce_node_count 0
    set level_nodes $n
    while { $level_nodes > $k } {
      # ceil(level_nodes / k)
      set level_nodes [ expr {($level_nodes - 1) / $k + 1} ]
      incr reduce_node_count $level_nodes
    }

    # Allocate intermediate nodes
    if { $reduce_node_count > 0 } {
      set node_create_spec [ list s:PartialStats 1 ]
      set node_create_specs [ lrepeat $reduce_node_count $node_create_spec ]
      set reduce_nodes [ adlb::multicreate {*}$node_create_specs ]
    }
   
    # Setup intermediate reduction
    set curr_structs $structs
    # Index into allocated reduce nodes
    set reduce_node_ix 0
    while { [ llength $curr_structs ] > $k } {
      set next_structs [ list ]

      set n [ llength $curr_structs ]
      for {set i 0} {$i < $n} {incr i $k} {
        # Slice of structs to reduce
        set slice [ lrange $curr_structs $i [ expr {$i + $k - 1} ]]

        # Output of reduction
        set output [ lindex $reduce_nodes $reduce_node_ix ]
        incr reduce_node_ix
        rule $slice [ list stat_combine_reduce $slice $output ]
        lappend next_structs $output
      }

      set curr_structs $next_structs
    }
    
    # Produce final output
    rule $curr_structs [ list stat_combine_final \
      [ lindex $curr_structs ] $n_out $mean_out $std_out ]

  }
  
  proc stat_combine_step { partials n_var mean_var M2_var } {
    upvar 1 $n_var n_accum
    upvar 1 $mean_var mean_accum
    upvar 1 $M2_var M2_accum
    set n_accum 0

    foreach struct_td $partials { 
      set struct [ retrieve_decr_struct $struct_td ]
      set n [ dict get $struct "n" ]
      set mean [ dict get $struct "mean" ]
      set M2 [ dict get $struct "M2" ]

      if { $n_accum == 0 } {
        set n_accum $n
        set mean_accum $mean
        set M2_accum $M2
      } else {
        # combine statistics
        set mean2 $mean_accum
        set n2 $n_accum
        # n' := n1 + n2
        set n_accum [ expr {$n_accum + $n} ]

        # weighted mean
        # mean' := (mean1 * n1 + mean2 * n2) / (n1 + n2)
        set mean_accum [ expr ( $mean2 * $n2 + $mean * $n) / \
                                          double( $n_accum ) ]

        #  diff := mean2 - mean1
        set diff [ expr {$mean - $mean2} ]

        # M2' := M2_1 + M2_2 + diff^2 * ( n1*n2 / (n1 + n2))
        set M2_accum [ expr $M2_accum + $M2 + \
                      (($diff**2) * ($n2 * $n / double($n_accum))) ]
      }
    }
  }
  
  # Intermediate reduction
  proc stat_combine_reduce { partials out } {
    #puts "REDUCE: $partials => $out"
    stat_combine_step $partials n mean M2
    set out_val [ dict create n $n mean $mean M2 $M2 ]
    store_struct $out $out_val s:PartialStats
  }

  # Final step of reduction: assign output variables
  proc stat_combine_final { partials n_out mean_out std_out } {
    #puts "REDUCE FINAL: $partials => $n_out $mean_out $std_out"
    stat_combine_step $partials n mean M2

    store_integer $n_out $n
    store_float $mean_out $mean
    store_float $std_out [ expr {sqrt($M2 / (double($n)))} ]
  }
}

