
// THIS-TEST-SHOULD-NOT-COMPILE
// Matching key type but mismatch value
main {
    float A[string];
    A["test"] = 0.1;
    f(A);
}


f (int A[string]) {
    trace("CALLED F");
}
