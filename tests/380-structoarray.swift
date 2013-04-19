type soa {
    int A[];
    int B[];
}


main {
    soa a;
    a.A[0] = 1;
    a.B[0] = 2;

    trace(a.A[0], a.B[0]);

}
