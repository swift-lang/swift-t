#include <builtins.swift>

main {
    int x[];
    int y[];
    x = [1:10];
    y = [1:10:2];

    assertEqual(x[0], 1, "x[0]");
    assertEqual(x[9], 10, "x[9]");
    assertEqual(y[0], 1, "y[0]");
    assertEqual(y[4], 9, "y[4]");
    assertEqual(sum_integer(x), 55, "sum of [1:10]");
    assertEqual(sum_integer(y), 25, "sum of [1:10:2]");
}
