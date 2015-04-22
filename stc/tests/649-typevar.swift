// COMPILE-ONLY-TEST 

// Check more deeply nested types
<T>
(T out) f (T A[][]) "package" "0.0.0" "f";

main {
    int A[][];
    A[0][0] = 1;
    A[0][1] = 1;
    A[1][0] = 1;
    int x = f(A);
    trace(x);
}


