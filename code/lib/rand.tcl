# Turbine builtin functions for random number generation

# All have signature:
#   f <STACK> <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs
# even if some of the arguments are not used
#
# init_rng should be called during turbine startup before 
# any other functions are used
#
# TODO: this currently doesn't do proper parallel random number generation

namespace eval turbine {

    # initialize this process's random number generator with a somewhat 
    #   unpredictable seed
    # TODO: not good randomness
    proc init_rng {} {
        # set seed [ expr [ clock clicks ] ^ [ pid ] ]
        set seed [ adlb::rank ]
        set x [ expr srand($seed) ]
    }

    # called with 0 args, returns a random float in [0.0, 1.0)
    proc random { parent o i } {
        nonempty o
        # no input args
        empty i
        set o_val [ expr rand() ]
        set_float $o $o_val
    }

    # called with 2 args: inclusive minimum and exclusive maximum of range
    proc randint { parent o i } {
        nonempty o i
        set lo [ lindex $i 0 ]
        set hi [ lindex $i 1 ]
        rule "randint-$lo-$hi" "$lo $hi" \
            $turbine::LOCAL "randint_body $o $lo $hi"
    }

    proc randint_body { o lo hi } {
        set lo_value [ get_integer $lo ]
        set hi_value [ get_integer $hi ]
        set o_value [ randint_impl $lo_value $hi_value ]
        set_integer $o $o_value
    }

    proc randint_impl { lo hi } {
        if { [ expr $lo >= $hi ] } {
            error "randint: empty range \[$lo, $hi)"
        }
        set range [ expr $hi - $lo ]
        # random value in [lo, hi) 
        # this works b/c rand() generates a number in [0.0, 1.0)
        return [ expr (int(rand() * $range)) + $lo ]
    }
}
