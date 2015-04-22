
import assert;

() f () {
    int A[][];

    A = g();
    assertEqual(A[0][0], 1, "[0][0]");
    assertEqual(A[0][1], 2, "[0][1]");
    assertEqual(A[1][1], 3, "[1][1]");
    assertEqual(A[1][0], 3, "[1][0]");
}

main {
    f();
}

(int A[][]) g () {
    A[0][0] = 1;
    A[0][1] = 2;
    A[1][1] = 3;
    A[1][0] = 3;
    trace(A[0][0], A[0][1], A[1][1], A[1][0]);
}
