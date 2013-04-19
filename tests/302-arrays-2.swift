// Test passing in array
(int r) getFirst(int A[]) {
    r = A[0];
}

(int r) getSecond(int A[]) {
   r = A[1];
}

(int A[]) constructArray() {
    A[0] = 9;
    A[1] = 8;
    A[2] = 7;
    A[3] = 6;
    A[4] = 5;
}

main {
    int A[];
    A[0] = 2;
    A[1] = 123;
    A[2] = 321;

    trace(getFirst(A));
    trace(getSecond(A));

    trace(getFirst(constructArray()));
    trace(getSecond(constructArray()));
}

