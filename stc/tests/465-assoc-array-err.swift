
// THIS-TEST-SHOULD-NOT-COMPILE
main {
    float A[];
    A[1] = 0.1;

    f(A);
}


f (float A[float]) {
    trace("CALLED F");
}
