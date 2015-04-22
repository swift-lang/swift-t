

main {
    int A[];
    A[f(0)] = 2;
    A[f(1)] = 3;
    // Check that we can wait on arrays, and
    // that optimizations exploiting that work
    wait (A) {
        trace(A[0], A[1]);
    }
}


(int r) f (int x) {
    r = x;
}
