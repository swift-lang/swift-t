#include <builtins.swift>

main {
    int A[] = [1,2,3];

    assertEqual(sum_integer(A), 6, "sum of A");
    assertEqual(sum_integer([4,5,6]), 15, "sum of [4,5,6]");
}
