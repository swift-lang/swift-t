
// THIS-TEST-SHOULD-NOT-COMPILE
main {
    float A[string];
    A["test"] = 0.1;

    f(A);
}


f (float A[]) {
    trace("CALLED F");
}
