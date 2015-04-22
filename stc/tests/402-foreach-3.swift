
import assert;

int A[];
int B[];
int C[];
// Test dependent foreach loops
A = fillup();

foreach n, i in A {
    // Calculate squares of numbers in A
    B[i] = n * n;
}

@async
foreach n, i in B {
    // Calculate cubes of numbers in A
    C[i] = A[i] * n;
}

assertEqual(C[7], 8*8*8, "C[7]");
assertEqual(C[0], 1, "C[0]");
assertEqual(C[8], 9*9*9, "C[8]");

(int A[]) fillup () {
    A[0] = 1;
    A[1] = 2;
    A[2] = 3;
    A[3] = 4;
    A[4] = 5;
    A[5] = 6;
    A[6] = 7;
    A[7] = 8;
    A[8] = 9;
}
