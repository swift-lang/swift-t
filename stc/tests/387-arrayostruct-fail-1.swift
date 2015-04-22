// THIS-TEST-SHOULD-NOT-COMPILE

type mystruct {
    int a;
    string b;
}

main {

    mystruct A[];

    mystruct mem;
    A[0].a = 2; // Don't allow this


    assertEqual(A[0].a, 1, "[0].a");
    assertEqual(A[0].b, "hello", "[0].b");
    assertEqual(A[2].a, 1, "[2].a");
    assertEqual(A[2].b, "hello", "[2].b");
}


(int r) f (int x) {
    r = x;
}
