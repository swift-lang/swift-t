
(int res[][]) g () {
    int a[];
    int b[];
    res[0] = a;
    res[1] = b;
    a[0] = 3;
    a[1] = 2;
    a[2] = 1;
    b[0] = 1;
}

(int res[]) h () {
    res[0] = -1;
    res[1] = -1;
    res[2] = -1;
}

(int res) f () {
    int B[][];
    int A[];
    B = g();
    // This looks like it should work but
    // on one branch A is ref to another array,
    //  on the other its locally allocated
    if (B[0][0]) {
        A = B[0];
    } else {
        A = h();
    }
    trace(A[0], A[1], A[2]);
    res = B[0][0];
}


main {
    int res;
    res = f();
    trace(res);
}

