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
    proc sum_integer { stack result inputs } {
        set container [ lindex $inputs 0 ]

        rule "sum-$container" $container $turbine::LOCAL $adlb::RANK_ANY \
            "sum_integer_body $stack $container $result 0 0 -1"
    }

    proc sum_integer_body { stack container result accum next_index n } {
        debug "sum_integer $container => $result"
        set CHUNK_SIZE 1024
        # TODO: could divide and conquer instead of doing linear search
        if { $n == -1 } {
          set n [ adlb::enumerate $container count all 0 ]
        }
        set i $next_index
        while { $i < $n } {
          set this_chunk_size [ expr {min( $CHUNK_SIZE, $n - $i )} ]
          set members [ adlb::enumerate $container members $this_chunk_size $i ]
          # puts "members of $container $i $this_chunk_size : $members"
          foreach turbine_id $members {
            # puts "turbine_id: $turbine_id"
            if { [ adlb::exists $turbine_id ] } {
                # add to the sum
                set val [ retrieve_integer $turbine_id ]
                #puts "C\[$i\] = $val"
                set accum [ expr {$accum + $val} ]
                incr i
            } else {
                # block until the next turbine id is finished,
                #   then continue running
                # puts "sum_integer_body $stack $container $result $accum $i $n"
                rule "sum-$container" $turbine_id $turbine::LOCAL $adlb::RANK_ANY \
                    "sum_integer_body $stack $container $result $accum $i $n"
                # return immediately without setting result
                return
            }
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
    proc sum_float { stack result inputs } {
        set container [ lindex $inputs 0 ]

        rule "sum-$container" $container $turbine::LOCAL $adlb::RANK_ANY \
            "sum_float_body $stack $container $result 0 0 -1"
    }

    proc sum_float_body { stack container result accum next_index n } {
        debug "sum_float $container => $result"
        set CHUNK_SIZE 1024
        # TODO: could divide and conquer instead of doing linear search
        if { $n == -1 } {
          set n [ adlb::enumerate $container count all 0 ]
        }
        set i $next_index
        while { $i < $n } {
          set this_chunk_size [ expr {min( $CHUNK_SIZE, $n - $i )} ]
          set members [ adlb::enumerate $container members $this_chunk_size $i ]
          # puts "members of $container $i $this_chunk_size : $members"
          foreach turbine_id $members {
            # puts "turbine_id: $turbine_id"
            if { [ adlb::exists $turbine_id ] } {
                # add to the sum
                set val [ retrieve_float $turbine_id ]
                #puts "C\[$i\] = $val"
                set accum [ expr {$accum + $val} ]
                incr i
            } else {
                # block until the next turbine id is finished,
                #   then continue running
                # puts "sum_float_body $stack $container $result $accum $i $n"
                rule "sum-$container" $turbine_id $turbine::LOCAL $adlb::RANK_ANY \
                    "sum_float_body $stack $container $result $accum $i $n"
                # return immediately without setting result
                return
            }
          }
        }
        # If we get out of loop, we're done
        store_float $result $accum
        read_refcount_decr $container
    }

    # calculate mean of an array of floats or ints
    proc avg { parent result container } {
        set NULL 0
        stats_impl $container $NULL $NULL $result $NULL $NULL $NULL $NULL $NULL
    }

    # calculate mean of an array of floats or ints
    proc std { parent result container } {
        set NULL 0
        stats_impl $container $NULL $NULL $NULL $NULL $NULL $result $NULL $NULL
    }

    proc stats { parent outputs container } {
        set NULL 0
        set mean [ lindex $outputs 0 ]
        set std [ lindex $outputs 1 ]
        stats_impl $container $NULL $NULL $mean $NULL $NULL $std $NULL $NULL
    }

    proc statagg { parent outputs container } {
        set NULL 0
        set n [ lindex $outputs 0 ]
        set mean [ lindex $outputs 1 ]
        set M2 [ lindex $outputs 2 ]
        stats_impl $container $n $NULL $mean $M2 $NULL $NULL $NULL $NULL
    }

    proc stats_impl { container n_out sum_out mean_out M2_out \
                    samp_std_out pop_std_out\
                    max_out min_out } {
        rule "stats-body-$container" $container $turbine::LOCAL $adlb::RANK_ANY \
            "stats_body $container $n_out $sum_out $mean_out $M2_out \
             $samp_std_out $pop_std_out $max_out $min_out \
             0.0 0.0 0.0 NOMIN NOMAX 0 -1"
    }

    # Calculate mean, standard deviation, max, min for array of float or int
    proc stats_body { container n_out sum_out mean_out M2_out \
                samp_std_out pop_std_out\
                max_out min_out sum_accum mean_accum M2_accum min_accum\
                max_accum next_index n } {
      debug "stats_body $container"
      set CHUNK_SIZE 1024
      if { $n == -1 } {
        set n [ adlb::enumerate $container count all 0 ]
      }
      set i $next_index
      while { $i < $n } {
        set this_chunk_size [ expr {min( $CHUNK_SIZE, $n - $i )} ]
        set members [ adlb::enumerate $container members $this_chunk_size $i ]
        foreach turbine_id $members {
          #puts "turbine_id: $turbine_id"
          if { [ adlb::exists $turbine_id ] } {
            # retrieve value and make sure it's floating point
            # so we don't get surprised by integer division
            set x [ retrieve $turbine_id ]
            set x [ expr {double($x)} ]
            puts "c\[$i\] = $x"
            if { $sum_out != 0 } {
              # avoid potential of overflow
              set sum_accum [ expr {$sum_accum $x} ]
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
            puts "mean_accum = $mean_accum"
            set M2_accum [ expr {$M2_accum + $delta*($x - $mean_accum)} ]
            incr i
          } else {
            # block until the next turbine id is finished then continue running
            rule "stats_body-$container" $turbine_id $turbine::LOCAL $adlb::RANK_ANY \
              "stats_body $container $n_out $sum_out \
                 $mean_out $M2_out \
                 $samp_std_out $pop_std_out $max_out $min_out \
                 $sum_accum $mean_accum $M2_accum \
                 $min_accum $max_accum $i $n"
            # return immediately without setting result
            return
          }
        }
      }
      # If we get out of loop, we're done
      if { $n_out != 0 } {
        puts "DEBUG n = $n n_out = $n_out"
        store_integer $n_out $n
      }

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
      read_refcount_decr $container
    }


    # take a container of PartialStats and summarize them
    # outputs are mean, stddev
    # note: we return population std dev
    proc stat_combine { parent outputs container } {
      set n_out [ lindex $outputs 0 ]
      set mean_out [ lindex $outputs 1 ]
      set std_out [ lindex $outputs 2 ]
      rule "stats-combine-$container" $container \
            $turbine::LOCAL $adlb::RANK_ANY "stat_combine_body $container $n_out $mean_out $std_out \
              0 0.0 0.0 0"
    }

    proc stat_combine_body { container n_out mean_out std_out \
                    n_accum mean_accum \
                    M2_accum next_index } {
      set keys [ container_list $container ]
      set keycount [ llength $keys ]
      set i $next_index
      while { $i < $keycount } {
        set key [ lindex $keys $i ]
        set struct [ container_lookup $container $key ]
        # puts "key: $key"
        # struct should be closed
        set n_id [ dict get $struct "n" ]
        set mean_id [ dict get $struct "mean" ]
        set M2_id [ dict get $struct "M2" ]
        if { [ adlb::exists $n_id ] && [ adlb::exists $mean_id ] \
            && [ adlb::exists $M2_id ] } {
          set n [ retrieve_integer $n_id ]
          set mean [ retrieve_float $mean_id ]
          set M2 [ retrieve_float $M2_id ]
          if { $i > 0 } {
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
          } else {
            set n_accum $n
            set mean_accum $mean
            set M2_accum $M2
          }
          incr i
        } else {
          rule "stats-combine-$container" \
            "$n_id $mean_id $M2_id" $turbine::LOCAL $adlb::RANK_ANY \
            "stat_combine_body $container $n_out $mean_out $std_out \
              $n_accum $mean_accum $M2_accum $i"
          return
        }
      }
      if { $n_accum == 0 } {
        error "mean and standard deviation not defined for sample size 0"
      }
      store_integer $n_out $n_accum
      store_float $mean_out $mean_accum
      store_float $std_out [ expr {sqrt($M2_accum / (double($n_accum)))} ]
      read_refcount_decr $container
    }
}
