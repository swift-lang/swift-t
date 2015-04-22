
(int ret) f (int x) {
    ret = x - 1;
}


() printAnArray (int X[]) {
   trace(X[0], X[1], X[2], X[3]); 
}

main {
    int M[][];
    int M1[];
    int M2[];
    
    M[0] = M1;
    M[1] = M2;

    M1[0] = 1;
    M1[1] = 2;
    M1[2] = 3;
    M1[3] = 4;
    M2[0] = 4;
    M2[1] = 3;
    M2[2] = 2;
    M2[3] = 1;

    // Check that we can pass in array ref
    printAnArray(M[f(1)]);
    printAnArray(M[f(2)]);
}





