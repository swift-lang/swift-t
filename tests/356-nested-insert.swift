
// Regression test for compiler bug with
// lvalue/rvalue nested array lookups


main {
    int A[][];
    int i = f(1), j = f(1);
    trace(A[i][j]);
    A[i][j] = 2;
}


(int o) f (int i) {
    o = i;
}
