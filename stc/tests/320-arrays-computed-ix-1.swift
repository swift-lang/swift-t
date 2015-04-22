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

(int res[]) replicate(int x) {
    res[0] = x;
    res[1] = x;
    res[2] = x;
    res[3] = x;
    res[4] = x;
    res[5] = x;
    res[6] = x;
    res[7] = x;
    res[8] = x;
    res[9] = x;
}

main {
    int A[];
    A[0] = 2;
    A[1] = 123;
    A[2] = 321;
    A[4] = 321;
    A[7] = 321;
   
    int B[];
    B = constructArray();
    trace(A[0]);
    trace(A[1]);

    trace(A[2*3-2]);
    trace(A[B[2]]);
}

