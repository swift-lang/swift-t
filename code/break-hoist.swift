#include <builtins.swift>

main {
    
    int A[][];
    foreach i in [1:id(5)] {
        foreach j in [0:id(0)] {
            A[j][j] = 1; // Can't be hoisted
            A[i][j] = 1; // A[i] lookup should be hoisted
        }
    }
}

(int o) id (int i) {
    o = i;
}
