// Basic test to make sure that multidimensional arrays work

main {
    int M[][];
    int M1[];
    
    M[0] = M1;

    M1[3] = 1;

    trace(M[0][3]);
    trace(M[1][3]);

    // Test that we can assign a more complex expression
    M[1] = M[0];
}
