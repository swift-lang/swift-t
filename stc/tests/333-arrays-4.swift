/*
 * Used to fail, now is valid
 */
(int x[]) f () {
    x[0] = 1;
}

main {
    int x = 0;
    int A[];
    int B[][];
    B[0] = f();

    // on one branch, A must be locally allocated,
    // on the other A must be a reference to an array allocated elsewhere
    if (x) {
        A = B[0];
    } else {
        A[0] = 1;
    }
    trace(A[0]);
}
