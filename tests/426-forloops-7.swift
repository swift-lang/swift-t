
/*
 * Test the waiton and waitonall annotations for for loops
 */

import assert;
import math;

main {
    int n = 20;
    int j;

    @waiton=j @waiton=i
    for (int i = 0, j = 1; i < n; i = i + 1, j = j * 2) {
        // Hopefully these both turned into local
        trace(i+i);
        trace(j+1);
    }

    int k;
    @waitonall
    for (int i = 0, k = 1; i < n; i = i + 1, k = k * 2) {
        // Hopefully these both turned into local
        trace(i+i);
        trace(k+1);
        assertEqual(k, toInt(2**i), "2^" + fromint(i));
    }
}
