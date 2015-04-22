
# generated from main-leaf.template.tcl

# dnl Define convenience macros
changecom(`dnl')
define(`getenv', `esyscmd(printf -- "$`$1' ")')

package provide main_leaf 0.0

# dnl Receive USER_LEAF from environment for m4 processing
set USER_LEAF getenv(USER_LEAF)

namespace eval main_leaf {

    proc main_leaf_wrap { rc A } {
	deeprule $A 1 0 "main_leaf::main_leaf_wrap_impl $rc $A" type $::turbine::WORK
    }

    proc main_leaf_wrap_impl { rc A } {

        global USER_LEAF

        set length [ adlb::container_size $A ]
        set tds [ adlb::enumerate $A dict all 0 ]
        set argv [ list ]
        # Fill argv with blanks
        dict for { i v } $tds {
            lappend argv 0
        }
        # Set values at ordered list positions
        dict for { i v } $tds {
            lset argv $i $v
        }
        set rc_value [ ${USER_LEAF}_extension {*}$argv ]
        turbine::store_integer $rc $rc_value
    }
}
