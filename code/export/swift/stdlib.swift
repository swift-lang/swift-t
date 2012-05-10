
// STDLIB.SWIFT

#ifndef STDLIB_SWIFT
#define STDLIB_SWIFT

(int o)   abs_integer (int i)   "turbine"  "0.0.2" "abs_integer";
(float o) abs_float   (float i) "turbine"  "0.0.2" "abs_float";

(string s) getenv(string key) "turbine" "0.0.2" "getenv";

// Random functions
(float o) random() "turbine" "0.0.2" "random";
// inclusive start, exclusive end
(int o) randint(int start, int end) "turbine" "0.0.2" "randint";

#endif
