
# Benchmarking utility functions

namespace eval bench {

    package require turbine 0.0.2

    package provide bench 0.0.1

    # usage: set1_float no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1_float { stack result delay } {
        turbine::rule "set1-$result" $delay $turbine::WORK \
            "bench::set1_float_body $result $delay"
    }

    proc set1_float_body { result delay } {

        variable event

        mpe_setup
        set delay_value [ retrieve_float $delay ]
        mpe::log $event(start_set1)
        after [ expr round($delay_value) ]
        mpe::log $event(stop_set1)
        store_integer $result 1
    }

    # usage: set1_integer no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1_integer { stack result delay } {
        turbine::rule "set1-$result" $delay $turbine::WORK \
            "bench::set1_integer_body $result $delay"
    }

    proc set1_integer_body { result delay } {
        set delay_value [ retrieve_integer $delay ]
        after $delay_value
        store_integer $result 1
    }

    # usage: set1_integer no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1rA_integer { stack result delay } {
        turbine::rule "set1r-$result" $delay $turbine::WORK \
            "bench::set1rA_integer_body $result $delay"
    }

    proc set1rA_integer_body { result delay } {

        variable event
        mpe_setup
        set delay_value [ retrieve_integer $delay ]
        mpe::log $event(start_set1rA)
        # randomized delay value: rdv
        set rdv [ expr rand() * $delay_value ]
        set rdv [ expr round($rdv) ]
        mpe::log $event(start_debug) "after:$rdv"
        after $rdv
        mpe::log $event(stop_set1rA)
        store_integer $result 1
    }

    # usage: set1_integer no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc set1rB_integer { stack result delay } {
        turbine::rule "set1r-$result" $delay $turbine::WORK \
            "bench::set1rB_integer_body $result $delay"
    }

    proc set1rB_integer_body { result delay } {

        variable event
        mpe_setup
        set delay_value [ retrieve_integer $delay ]
        # randomized delay value: rdv
        set rdv [ expr rand() * $delay_value ]
        set rdv [ expr round($rdv) ]
        mpe::log $event(start_set1rB)
        after $rdv
        mpe::log $event(stop_set1rB)
        store_integer $result 1
    }

    # usage: set1_integer no_stack result delay
    # delay in milliseconds: rounded to nearest whole millisecond
    proc add4 { stack result inputs } {
        set w [ lindex $inputs 0 ]
        set x [ lindex $inputs 1 ]
        set y [ lindex $inputs 2 ]
        set z [ lindex $inputs 3 ]
        turbine::rule "add4-$result" "$w $x $y $z" $turbine::WORK \
            "bench::add4_body $result $w $x $y $z"
    }

    proc add4_body { result w x y z } {
        set w_value [ retrieve_integer $w ]
        set x_value [ retrieve_integer $x ]
        set y_value [ retrieve_integer $y ]
        set z_value [ retrieve_integer $z ]

        set r [ expr $w_value + $x_value + $y_value + $z_value ]
        store_integer $result $r
    }

    # Sum all of the values in a container of integers
    # inputs: [ list c r ]
    # c: the container
    # r: the turbine id to store the sum into
    proc bench_sum_integer { stack result inputs } {
        set container [ lindex $inputs 0 ]

        mpe_setup
        variable event
        mpe::log $event(start_sum)
        turbine::rule "sum-$container" $container $turbine::LOCAL \
            "bench::bench_sum_integer_body $stack $container $result 0 0 -1"
    }

    proc bench_sum_integer_body { stack container result accum next_index n } {

        variable event
        turbine::debug "sum_integer $container => $result"
        set CHUNK_SIZE 1024
        # TODO: could divide and conquer instead of doing linear search
        if { $n == -1 } {
          set n [ adlb::enumerate $container count all 0 ]
        }
        set i $next_index
        while { $i < $n } {
          set this_chunk_size [ expr min( $CHUNK_SIZE, $n - $i ) ]
          set members [ adlb::enumerate $container members $this_chunk_size $i ]
          #puts "members of $container $i $this_chunk_size : $members"
          foreach turbine_id $members {
            #puts "turbine_id: $turbine_id"
            if { [ adlb::exists $turbine_id ] } {
                # add to the sum
                set val [ retrieve_integer $turbine_id ]
                #puts "C\[$i\] = $val"
                set accum [ expr $accum + $val ]
                incr i
            } else {
                # block until the next turbine id is finished,
                #   then continue running
                turbine::rule "sum-$container" $turbine_id $turbine::LOCAL \
                    "bench::bench_sum_integer_body $stack $container $result $accum $i $n"
                # return immediately without setting result
                return
            }
          }
        }
        # If we get out of loop, we're done
        store_integer $result $accum
        mpe::log $event(stop_sum)
    }
}
