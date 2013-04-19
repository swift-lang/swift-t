//THIS-TEST-SHOULD-NOT-COMPILE

(int a) f (int b[]) {
    int A[];
    A[0] = 1;
    A[1] = 2;
    b = A; // This assignment invalid

    a = 0;
}
