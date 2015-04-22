// Test to make sure passing multidimensional arrays as arguments works

() trace_my_array(int M[][]) {
    trace(M[0][3]);
    trace(M[0][4]);
    trace(M[1][5]);
    trace(M[1][6]);
}

main {
    int M[][];
    int M1[];
    int M2[];
    
    M[0] = M1;
    M[1] = M2;

    M1[3] = 1;
    M1[4] = 2;
    M2[5] = 3;
    M2[6] = 4;
    
    trace_my_array(M);
}
