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
    // Check that indexing works with expression
    trace(constructArray()[0]);
    trace(constructArray()[1]);

    // Check that indexing works with more complex expression
    trace(replicate(2+3-1)[0]);
    trace(replicate(2+3-1*3)[9]);
}

