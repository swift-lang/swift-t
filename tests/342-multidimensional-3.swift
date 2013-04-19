// Basic test to make sure that returning multidimensional arrays works

(int r[]) M1 () {
    r[3] = 1;
    r[4] = 2;
}
(int r[]) M2 () {
    r[5] = 3;
    r[6] = 4;
}

main {
    int M[][];
    
    M[0] = M1();
    M[1] = M2();

    trace(M[0][3]);
    trace(M[0][4]);
    trace(M[1][5]);
    trace(M[1][6]);
}
