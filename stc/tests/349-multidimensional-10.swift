
(int ret) f (int x) {
    ret = x - 1;
}


() printArrays (int X[], int xoff,
                int Y[], int yoff) {
   trace(X[0+xoff], X[1+xoff], X[2+xoff], X[3+xoff],
        Y[0+yoff], Y[1+yoff], Y[2+yoff], Y[3+yoff]); 
}

main {
    int M[][];
    int M1[];
    int M2[];
    
    M[0] = M1;
    M[1] = M2;

    M1[2] = 1;
    M1[3] = 2;
    M1[4] = 3;
    M1[5] = 4;
    M2[5] = 4;
    M2[6] = 3;
    M2[7] = 2;
    M2[8] = 1;

    // Check that we can pass in array refs
    // mixed with other arguments ok
    printArrays(M[f(1)], 2, M[f(2)], 5);
}





