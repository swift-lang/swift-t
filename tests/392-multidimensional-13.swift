// Test for nested array insertion with computed indices
() f () {
    int A[][];
    A[0][i()] = 1;
    A[i()][1] = 2;
    A[j()][0] = 3;
    trace(A[0][0], A[0][1], A[1][0]);
}


(int r) i () {
    r = 0;
}

(int r) j () {
    r = 1;
}

main {
    f();
}
