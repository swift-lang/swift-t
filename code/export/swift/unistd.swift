
// UNISTD.SWIFT

#ifndef UNISTD_SWIFT
#define UNISTD_SWIFT

/* Model arg functions as pure, since they will be deterministic
 * within the scope of a program */

@pure
(int c)    argc()
    "turbine" "0.0.2" "argc_get";
@pure
(string s) args()
    "turbine" "0.0.2" "args_get";
@pure
(boolean b) argv_contains(string key)
    "turbine" "0.0.2" "argv_contains";
argv_accept(string... keys)
    "turbine" "0.0.2" "argv_accept";
@pure
(string s) argv(string|int key, string... default_val)
    "turbine" "0.0.2" "argv_get";

// Do not optimize this- it is for tests
(void v) sleep(float seconds) "turbine" "0.0.4" "sleep";

#endif
