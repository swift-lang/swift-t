
// THIS-TEST-SHOULD-NOT-COMPILE
// Can't use arrays as keys

typedef a int[];

main {
    float A[a];

    a x;
    x[0] = 1;

    A[x] = 0.1;

    trace(A[x]);
}
