
import assert;

main {
    int A[] = f(g(1), g(2));
    assertEqual(A[1], 1, "A[1]");
    assertEqual(A[2], 4, "A[2]");
    int B[] = f(g(3), g(4));
    assertEqual(B[3], 9, "B[1]");
    assertEqual(B[4], 16, "B[2]");
    trace(A[1], A[2], B[3], B[4]);
}

(int r) g (int x) {
    r = x;
}

(int A[]) f (int a, int b) {
    wait (a) {
      wait (a, b) {
        foreach i in [a:b] {
            A[i] = i*i;
        }
      }
    }
}

@sync
(int A[]) f2 (int a, int b) {
    wait (a) {
      wait (a, b) {
        foreach i in [a:b] {
            A[i] = i*i;
        }
      }
    }
}
