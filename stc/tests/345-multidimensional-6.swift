// Test to check more complex array assignments

main {
    int M[][];
    int N[][];

    int N1[];
    N1[0] = 12;
    N1[123] = 13;

    int M1[];
    int M2[];
    M1[3] = 1;
    M1[4] = 123;
    M2[1] = N1[0];
    M2[2] = M1[3];


    N[0] = N1;
    N[2] = M[3];
    
    M[0] = M1;
    M[1] = M[0];
    M[2] = M[1];
    M[3] = N[0];
    M[4] = N[2];

    trace(M[4][123]);
    trace(M[1][4]);

}
