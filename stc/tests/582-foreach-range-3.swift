
// Check that it compiles to good code with arrays

import stats;

main {
    int A[][];
    int n = 20;
    @async
    foreach j in [0:n] {
        A[0][j] = n;
    }

    foreach i in [1:n] {
        @async
        foreach j in [0:n] {
            A[i][j] = A[i-1][j] + 1;
        }
    }

    foreach a in A {
        trace(sum_integer(a));
    }
}
