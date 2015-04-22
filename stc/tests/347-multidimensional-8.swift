() f (int C[]) {

}

main {
    // regression test for corner case where compiler incorrectly
    // decides that this is invalid
    int A[][];
    int B[];
    A[0] = B; // put empty array in A[0]


    int C[];
    switch (1) {
        case 1:
            f(C);
        case 2:
        default: 
            f(C);
    }
    
    int D[];
    switch (1) {
        case 1:
        case 2:
            f(C);
    }
    f(D);
}
