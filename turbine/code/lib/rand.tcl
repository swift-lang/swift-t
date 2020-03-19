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

# Turbine builtin functions for random number generation

# All have signature:
#   f <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs
# even if some of the arguments are not used
#
# init_rng should be called during turbine startup before
# any other functions are used
#
# TODO: this currently doesn't do proper parallel random number generation

namespace eval turbine {

    # Initialize this process's RNG based on
    #       TURBINE_SRAND and/or MPI rank
    # TODO: Upgrade Turbine to use SPRNG
    # Note: Call this after adlb::init so that rank is valid
    proc init_rng {} {
        global env
        set rank [ adlb::comm_rank ]

        if { [ getenv_integer TURBINE_SRAND x s ] } {
            if { $rank == 0 } {
                log "TURBINE_SRAND: $s"
            }
        } else {
            set s 0
        }
        expr srand($s + $rank)
    }

    # called with 0 args, returns a random float in [0.0, 1.0)
    proc random { o i } {
        nonempty o
        # no input args
        empty i
        set o_val [ expr {rand()} ]
        store_float $o $o_val
    }

    # called with 2 args: inclusive minimum and exclusive maximum of range
    proc randint { o i } {
        nonempty o i
        set lo [ lindex $i 0 ]
        set hi [ lindex $i 1 ]
        rule "$lo $hi" "randint_body $o $lo $hi" \
            name "randint-$lo-$hi"
    }

    proc randint_body { o lo hi } {
        set lo_value [ retrieve_decr_integer $lo ]
        set hi_value [ retrieve_decr_integer $hi ]
        set o_value [ randint_impl $lo_value $hi_value ]
        store_integer $o $o_value
    }

    # Obtain random value in [lo, hi)
    # This works b/c rand() generates a number in [0.0, 1.0)
    proc randint_impl { lo hi } {
        if { [ expr {$lo >= $hi} ] } {
            turbine_error "randint: illegal range \[$lo, $hi)"
        }
        set range [ expr {$hi - $lo} ]
        return [ expr {(int(rand() * $range)) + $lo} ]
    }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
