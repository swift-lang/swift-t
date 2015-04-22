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

# Turbine reduction operations

namespace eval turbine {

    namespace export reduce_sum_integer

    proc array_max_float { result container } {
        deeprule $container 1 [ list false false ] \
            "turbine::array_max_float_body $result $container"
    }
    proc array_max_float_body { result container } {
        set m [ expr -Inf ]
        foreach i [ container_list $container ] {
            set td [ container_lookup $container $i ]
            set v  [ retrieve_decr_float $td ]
            if { $v > $m } { set m $v }
        }
        store_float $result $m
    }

    proc array_min_float { result container } {
        deeprule $container 1 [ list false false ] \
            "turbine::array_min_float_body $result $container"
    }
    proc array_min_float_body { result container } {
        log "array_min_float: <$container>"
        set m [ expr Inf ]
        foreach i [ container_list $container ] {
            set td [ container_lookup $container $i ]
            set v  [ retrieve_decr_float $td ]
            if { $v < $m } { set m $v }
        }
        log "array_min_float: <$container> => $m"
        store_float $result $m
    }

    proc reduce_sum_integer { result A } {
        deeprule $A 2 [ list false false ] \
            "reduce_sum_integer_body $result $A" \
            name "reduce_sum_integer" type $::turbine::CONTROL
    }
    proc reduce_sum_integer_body { result A } {
        set R [ dict create ]
        set D [ adlb::enumerate $A dict all 0 ]
        dict for { i td_h } $D {
            # puts "dict: $i $td_h"
            set L [ adlb::enumerate $td_h dict all 0 ]
            dict for { h td_x } $L {
                # puts "  dict: $h $td_x"
                set x_value [ retrieve_integer $td_x ]
                dict incr R $h $x_value
            }
        }

        dict for { h s_value } $R {
            # puts "reduce_sum: h: $h s: $s_value"
            literal s integer $s_value
            container_insert $result $h $s ref
        }
        write_refcount_decr $result
    }

    proc reduce_splice_string { result S } {
        deeprule $S 2 [ list false false ] \
            "reduce_splice_string_body $result $S" \
            name "reduce_splice_string" type $::turbine::CONTROL
    }
    proc reduce_splice_string_body { result S } {
        log "reduce_splice_string_body: $result <- $S"
        set R [ dict create ]
        set D [ adlb::enumerate $S dict all 0 ]
        dict for { i td_h } $D {
            set L [ adlb::enumerate $td_h dict all 0 ]
            dict for { h td_x } $L {
                # puts "  dict: $h $td_x"
                set x_value [ retrieve_string $td_x ]
                if { [ dict exists $R $h ] } {
                    set t [ dict get $R $h ]
                    check [ string equal $t $x_value ] \
                        [ join "reduce_splice_string: for index $h: " \
                               "$t != $x_value" ]
                } else {
                    dict set R $h $x_value
                }
            }
        }

        dict for { h s_value } $R {
            debug "reduce_splice: h: $h s: $s_value"
            literal s string $s_value
            container_insert $result $h $s ref
        }
        write_refcount_decr $result
    }
}
