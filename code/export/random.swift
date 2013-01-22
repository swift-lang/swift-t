#ifndef RAND_SWIFT
#define RAND_SWIFT

(float o) random() "turbine" "0.0.2" "random"
    [ "set <<o>> [ expr rand() ]" ];

// inclusive start, exclusive end
(int o) randint(int start, int end) "turbine" "0.0.2" "randint"
    [ "set <<o>> [ turbine::randint_impl <<start>> <<end>> ]" ];

(void v) srand(int seed)
"turbine" "0.0.2" "random"
[ "expr srand(<<seed>>) ; turbine::log \"srand <<seed>>\"" ];

#endif // RAND_SWIFT
