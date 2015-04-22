// This fails because the arrays inside the struct are never closed

type soa {
    int A[];
    int B[];
}

() f (soa a) {
    trace(a.A[0], a.B[0]);
}

() g (int A[], int B[]) {
    trace(A[0], B[0]);
}

main {
    soa a;
    a.A[0] = 1;
    a.B[0] = 2;

    f(a);
    g(a.A, a.B);
}

