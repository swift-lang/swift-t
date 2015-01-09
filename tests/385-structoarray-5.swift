
import assert;
// Regression test - could not do copy of struct of arrays 

(int r) id (int x) {
    r = x;
}

main {
    soa a;

    a.A = a.B; // whole array assignment
    a.B[0] = 1;
    a.B[1] = 2;
    a.B[id(2)] = 3;


    soa b;
    b = a; // recursive copy!

    assertEqual(a.B[0], 1, "a.B[0]");
    assertEqual(a.B[1], 2, "a.B[1]");
    assertEqual(a.B[2], 3, "a.B[2]");
    assertEqual(a.A[0], 1, "a.A[0]");
    assertEqual(a.A[1], 2, "a.A[1]");
    assertEqual(a.A[2], 3, "a.A[2]");
    assertEqual(b.B[0], 1, "b.B[0]");
    assertEqual(b.B[1], 2, "b.B[1]");
    assertEqual(b.B[2], 3, "b.B[2]");
    assertEqual(b.A[0], 1, "b.A[0]");
    assertEqual(b.A[1], 2, "b.A[1]");
    assertEqual(b.A[2], 3, "b.A[2]");
}

type soa {
    int A[];
    int B[];
}
