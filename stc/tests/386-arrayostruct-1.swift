
import assert;

type mystruct {
    int a;
    string b;
}

main {

    mystruct A[];

    mystruct mem;
    mem.a = 1;
    mem.b = "hello";

    A[0] = mem;
    A[1] = mem;
    A[f(2)] = mem;

    trace(A[0].a, A[0].b, A[2].a, A[2].b);

    assertEqual(A[0].a, 1, "[0].a");
    assertEqual(A[0].b, "hello", "[0].b");
    assertEqual(A[2].a, 1, "[2].a");
    assertEqual(A[2].b, "hello", "[2].b");
}


(int r) f (int x) {
    r = x;
}
