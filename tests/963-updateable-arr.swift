/*
  Basic test for arrays of updateables
 */

import sys;
import assert;

// SKIP-THIS-TEST

main {
    updateable_float counters[boolean];
    updateable_float div = 0;
    updateable_float notdiv = 0;

    // TODO: how to initialize?
    counters[true] = div;
    counters[false] = notdiv;

    int max = toint(argv("N"));
    foreach i in [1:max] {
        boolean divisible = i %% 13 == 0;
        // TODO: increment directly
        updateable_float count = counters[divisible];
        count <incr> := 1;
    }

    wait (counters[true], counters[false]) {
        assertEqual(counters[true], max %/ 13, "divisible");
        assertEqual(counters[false], max - max %/ 13, "not divisible"); 
    }
}
