// dummy function
(int r) array_lookup(int A[], int i) {
    r = A[i];
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
   
    int B[];
    B = constructArray();
    trace(A[0]);
    trace(A[1]);

    trace(A[2*3-2]);
    trace(A[B[2]]);
    trace(array_lookup(A, B[2]));
    trace(999, A[B[0]]);
    trace(array_lookup(B, A[B[0]]));

    A[0] = 2;
    A[1] = 123;
    A[1+2] = B[0]; // A[3] = 9
    A[2] = 321;
    A[4] = 321;
    A[5] = 321;
    A[6] = 321;
    A[7] = 321;
    A[9] = 2;
    A[A[1]] = 3;  // A[123] = 3
    A[(2*3+243)*300000] = 100;

    int i;
    i = (2*3+243)*300000;
    trace(A[i]);
}

