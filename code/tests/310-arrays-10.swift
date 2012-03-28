#include <builtins.swift>
// Test array copying by value
//SKIP-THIS-TEST
main {

    int x[] = [1,2,3];

    int y[] = x;
    
    trace(y[0], y[1], y[2]);


    float A[];

    // Non-contiguous indicies
    A[2] = 3.14;
    A[54] = 1.0;
    A[55] = 2.0;

    float B[] = A;

    trace(B[2], B[54], B[55]);
}
