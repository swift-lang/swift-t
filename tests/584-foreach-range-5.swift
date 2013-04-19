
import assert;

main {
    int A[];
    A[0] = 0;
    A[1] = 1;

    @unroll=20
    foreach i in [2:45] {
        // Optimizer (in future) should be able to avoid repeated array
        //  accesses after unrolling
        A[i] = A[i-1] + A[i-2];
    }

    trace(A[45]);
    assertEqual(A[45], 1134903170, "fib(50)");
}
