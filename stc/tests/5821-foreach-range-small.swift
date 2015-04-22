
// Check that it compiles to good code with arrays

import stats;

main {
    int A[][];
    int n = 2;
    foreach j in [0:n] {
        A[0][j] = n;
    }

    foreach i in [1:n] {
        foreach j in [0:n] {
            A[i][j] = A[i-1][j] + 1;
        }
    }

    trace(sum_integer(A[0]));
}
