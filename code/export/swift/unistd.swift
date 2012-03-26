#ifndef __STC_UNISTD_SWIFT__
#define __STC_UNISTD_SWIFT__

// UNISTD.SWIFT

(int c)    argc()                 "turbine" "0.0.2" "argc_get";
(string s) args()                 "turbine" "0.0.2" "args_get";
(boolean b) argv_contains(string key) "turbine" "0.0.2" "argv_contains";
argv_accept(string... keys)       "turbine" "0.0.2" "argv_accept";
(string s) argv(string|int key, string... default_val)
"turbine" "0.0.2" "argv_get";

#endif
