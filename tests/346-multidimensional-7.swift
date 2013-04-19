
(int result[]) f (int x) {
    result[0] = x + 0;
    result[1] = x + 1;
    result[2] = x + 2;
    result[3] = x + 3;
}

main {
    int A[][];

    A[0] = f(0);
    A[1] = f(1);
    A[2] = f(2);
    A[3] = f(3);

    trace(A[0][0], A[0][1], A[0][2], A[0][3]);
    trace(A[1][0], A[1][1], A[1][2], A[1][3]);
    trace(A[2][0], A[2][1], A[2][2], A[2][3]);
    trace(A[3][0], A[3][1], A[3][2], A[3][3]);
}
