import assert;

type soa {
    int A[];
    int B[];
}

type sosoa {
    soa X;
    int C[];
    int D[];
}

(int r) squared_rec(int x, int y) {
    if (x == 0) {
        r = 0;
    } else {
        r = y + squared_rec(x-1, y);
    }
}

(int r) squared (int x) {
    r = squared_rec(x, x);
}

(int r) g() {
    r = 0;
}

(sosoa r) f () {
    r.X.A[0] = r.X.B[0];
    r.X.B[g()] = squared(9);
    r.C[g() - 2] = r.X.A[0];
    r.C[squared(0) * 2] = r.X.A[0];

    trace(r.X.B[0], r.C[0]);
    // expecting (81, 81)

    assertEqual(r.X.B[0], 81, "B");
    assertEqual(r.C[0], 81, "C");
}


main {
    f();
}
