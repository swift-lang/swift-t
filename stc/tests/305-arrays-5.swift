
import assert;

(int r) is_even (int x) {
    if (x == 0) {
        r = 1; // is even
    } else {
        if (x == 1) {
            r = 0; // is odd
        } else {
            r = is_even(x-2);
        }
    }
}

main {
    // Check to see that array closing works ok with conditionals
    int A[];
    A[0] = 2;

    if (is_even(32)) {
        A[1] = 3;

    } else {
        A[1] = 4;
    }

    if (is_even(21)) {
        A[2] = 23;
    } else {
        A[2] = 32;
    }
    A[3] = 0;

    trace(A[0], A[1], A[2], A[3]);
    assertEqual(A[0], 2, "0");
    assertEqual(A[1], 3, "1");
    assertEqual(A[2], 32, "2");
    assertEqual(A[3], 0, "3");
}
