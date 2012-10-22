#include <builtins.swift>
// THIS-TEST-SHOULD-NOT-RUN

// CHeck that double writes are caught

main {
    int A[];
    A[id(1)] = 1;
    A[id(1)] = 2;
}

(int o) id (int i) {
    o = i;
}
