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
# Turbine builtin assert functions for debugging

namespace eval turbine {

    # assert(cond, msg)
    # If cond is 0, then terminate program, printing msg
    proc assert { result inputs } {
        set cond [ lindex $inputs 0 ]
        set msg [ lindex $inputs 1 ]
        rule "$cond $msg" "assert_body $result $cond $msg" \
            name "assert-$cond-$msg"
    }

    proc assert_body { result cond msg } {
        set cond_value [ retrieve_decr_integer $cond ]
        set msg_value [ retrieve_decr_string $msg ]
        assert_impl $cond_value $msg_value
        store_void $result
    }

    proc assert_impl { cond msg } {
        if { $cond == 0 } {
            turbine_error "Assertion failed!: $msg"
        } else {
            log "assert passed: ($msg)"
        }
    }

    # assert(arg1, arg2, msg)
    # if arg1 != arg2 according to Tcl's comparison rules
    #  (e.g. "1" == 1 and 1 == 1)
    # then crash, printing the two values and the provided msg
    proc assertEqual { result inputs } {
        set arg1 [ lindex $inputs 0 ]
        set arg2 [ lindex $inputs 1 ]
        set msg [ lindex $inputs 2 ]
        rule "$arg1 $arg2 $msg" "assertEqual_body $result $arg1 $arg2 $msg" \
            name "assertEqual-$arg1-$arg2-$msg"
    }

    proc assertEqual_body { result arg1 arg2 msg } {
        set arg1_value [ retrieve_decr $arg1 ]
        set arg2_value [ retrieve_decr $arg2 ]
        set msg_value  [ retrieve_decr_string $msg ]
        assertEqual_impl "$arg1_value" "$arg2_value" "$msg_value"
        store_void $result
    }

    proc assertEqual_impl { arg1 arg2 msg } {
        if { $arg1 != $arg2 } {
            turbine_error "Assertion failed $arg1 != $arg2: $msg"
        } else {
            log "assertEqual $arg1 $arg2 passed: $msg"
        }
    }

    # assertLT(arg1, arg2, msg)
    # if arg1 >= arg2 according to TCL's comparison rules
    # then crash, printing the two values and the provided msg
    proc assertLT { result inputs } {
        set arg1 [ lindex $inputs 0 ]
        set arg2 [ lindex $inputs 1 ]
        set msg [ lindex $inputs 2 ]
        rule "$arg1 $arg2 $msg" "assertLT_body $result $arg1 $arg2 $msg" \
            name "assertLT-$arg1-$arg2-$msg"
    }

    proc assertLT_body { result arg1 arg2 msg } {
        set arg1_value [ retrieve_decr $arg1 ]
        set arg2_value [ retrieve_decr $arg2 ]
        set msg_value [ retrieve_decr_string $msg ]
        if { $arg1_value >= $arg2_value } {
            error "Assertion failed $arg1_value >= $arg2_value: $msg_value"
        } else {
            log "assertLT: $arg1_value $arg2_value"
        }
        store_void $result
    }

    # assertLTE(arg1, arg2, msg)
    # if arg1 >= arg2 according to TCL's comparison rules
    # then crash, printing the two values and the provided msg
    proc assertLTE { result inputs } {
        set arg1 [ lindex $inputs 0 ]
        set arg2 [ lindex $inputs 1 ]
        set msg [ lindex $inputs 2 ]
        rule "$arg1 $arg2 $msg" "assertLTE_body $result $arg1 $arg2 $msg" \
            name "assertLTE-$arg1-$arg2-$msg"
    }

    proc assertLTE_body { result arg1 arg2 msg } {
        set arg1_value [ retrieve_decr $arg1 ]
        set arg2_value [ retrieve_decr $arg2 ]
        set msg_value [ retrieve_decr_string $msg ]
        if { $arg1_value > $arg2_value } {
            error "Assertion failed $arg1_value > $arg2_value: $msg_value"
        } else {
            log "assertLTE: $arg1_value $arg2_value"
        }
        store_void $result
    }
}
