
// STDLIB.SWIFT

#ifndef STDLIB_SWIFT
#define STDLIB_SWIFT

/* Model getenv as pure because it will be deterministic within
 * the context of a program
 */
@pure  
(string s) getenv(string key) "turbine" "0.0.2" "getenv";

// Random functions
@builtin_op=RANDOM
(float o) random() "turbine" "0.0.2" "random";
// inclusive start, exclusive end
@builtin_op=RAND_INT
(int o) randint(int start, int end) "turbine" "0.0.2" "randint";

#endif
