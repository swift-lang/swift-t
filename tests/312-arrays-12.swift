// THIS-TEST-SHOULD-NOT-RUN

// CHeck that double writes are caught

main {
    int A[];
    A[id(1)] = 1;
    A[id(1)] = 2;
    // Prevent A getting optimized out
    trace(A[1]);
}

(int o) id (int i) {
    o = i;
}
