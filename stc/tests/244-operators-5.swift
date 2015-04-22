
import assert;

// Sanity check for max and min
main {
    // Let constant fold
    assertEqual(max_integer(2, 4), 4, "max_integer");
    assertEqual(min_integer(2, 4), 2, "min_integer");

    assertEqual(max_float(3.212, 4.232), 4.232, "max_float");
    assertEqual(min_float(3.212, 4.232), 3.212, "min_float");

    // Disable constant folding, local arith ops
    assertEqual(max_integer(f(2), f(4)), 4, "max_integer");
    assertEqual(min_integer(f(2), f(4)), 2, "min_integer");

    assertEqual(max_float(g(3.212), g(4.232)), 4.232, "max_float");
    assertEqual(min_float(g(3.212), g(4.232)), 3.212, "min_float");

    // Force use of local arith ops
    int n = f(0);
    if (n == 0) {
        // value of n is now known
        assertEqual(max_integer(2+n, 4+n), 4, "max_integer");
        assertEqual(min_integer(2+n, 4+n), 2, "min_integer");

    }
    float m = g(0.0);
    if (m != 3.142) {
        // value of m is now known
        assertEqual(max_float(3.212+m, 4.232+m), 4.232, "max_float");
        assertEqual(min_float(3.212+m, 4.232+m), 3.212, "min_float");
    }
}

(int r) f (int x) {
    r = x;
}

(float r) g (float x) {
    r = x;
}
