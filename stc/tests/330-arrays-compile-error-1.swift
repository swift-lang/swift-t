//THIS-TEST-SHOULD-NOT-COMPILE

(int r) f(int A[]) {
   r = A[1];
   A[2] = 3;
}


main {
    int A[];
    A[0] = 2;
    A[1] = 123;
    A[2] = 321;
    
    trace(f(A));

}


