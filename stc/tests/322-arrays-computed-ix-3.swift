
(int r) f (int x) {
    r = x * x;
}

(int r) plusone(int i) {
    r = i + 1;
}

(int A[]) constructArray(int i) {
    A[plusone(0)] = f(i);
    A[plusone(1)] = f(i+1);
    A[plusone(2)] = f(i+2);
    A[plusone(3)] = f(i+3);
    A[plusone(4)] = f(i+4);
}


main {
    int B[];
    // Lookup with immediate indices, but fill array
    // by computed indices, so that lookups occur before
    // stores
    B = constructArray(22);
    trace(B[1]);
    trace(B[2]);
    trace(B[3]);
    trace(B[4]);
    trace(B[5]);
}

