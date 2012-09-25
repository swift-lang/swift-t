
// STDLIB.SWIFT

#ifndef STDLIB_SWIFT
#define STDLIB_SWIFT

/* Model getenv as pure because it will be deterministic within
 * the context of a program
 */
@pure  
(string s) getenv(string key) "turbine" "0.0.2" "getenv"
    [ "set <<s>> turbine::getenv_impl <<key>>" ];

// Random functions
(float o) random() "turbine" "0.0.2" "random"
    [ "set <<o>> [ expr rand() ]" ];
// inclusive start, exclusive end
(int o) randint(int start, int end) "turbine" "0.0.2" "randint"
    [ "set <<o>> [ turbine::randint_impl <<start>> <<end>> ]" ];

#endif
