
type soa {
    int A[];
    int B[];
}


type soa2 {
    int myarray[];
    int x;
    int anotherarray[];
    soa inner;
}

(int r) f () {
    r = 3;
}

main {
    soa a;

    a.A[0] = 1;
    a.A[1] = 2;
    a.A[2] = 3;
    a.A[f()] = 4;
    a.B[f()] = 5;

    trace(a.A[0], a.A[1], a.A[2], a.A[3]);
}
