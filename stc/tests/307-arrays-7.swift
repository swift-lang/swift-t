
import assert;
import stats;

(int r) g (int x) {
    r = 2 + x;
}

(int M[]) f () {
    M[0] = 1;
    M[1] = 2;
    M[2] = 3;
    M[3] = g(1);
    M[4] = M[3] + g(2);
    M[5] = M[4] + g(3);
    M[23] = M[4] + g(3);
    // Total of array should be:
    // 1 + 2 + 3 + 3 + 7 + 12 + 12 = 40
}

main {
    trace(sum_integer(f()));
    assertEqual(sum_integer(f()), 40,"immediate");

    // Check with deferred calculation
    int M[];
    M[0] = f()[0];
    M[1] = 2;
    M[2] = 3;
    M[3] = f()[3];
    M[4] = f()[4];
    M[5] = f()[5];
    M[22] = f()[23];
    assertEqual(sum_integer(M), 40,"delayed");
}
