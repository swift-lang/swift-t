
// Check that it compiles to good code with arrays

#include <builtins.swift>
#include <swift/stats.swift>

main {
    int A[][];
    int n = 20;
    @sync
    foreach j in [0:n] {
        A[0][j] = n;
    }

    foreach i in [1:n] {
        @sync
        foreach j in [0:n] {
            A[i][j] = A[i-1][j] + 1;
        }
    }

    foreach a in A {
        trace(sum_integer(a));
    }
}
