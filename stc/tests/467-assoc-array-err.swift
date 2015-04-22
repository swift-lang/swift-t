
// THIS-TEST-SHOULD-NOT-COMPILE
main {
    float A[string];
    float B[int];

    A = B;
}


f (float A[]) {
    trace("CALLED F");
}
