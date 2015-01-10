// We fail to correctly refcount a.B and a.B

type soa {
    int A[];
    int B[];
}

(int r) q () {
    r = 0;
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
    
    // check that array closing works ok with conditionals
    if (q()) {
        a.A[1] = 2;
    }  else {
        a.B[1] = 2;
    }

    f(a);
    g(a.A, a.B);
}

