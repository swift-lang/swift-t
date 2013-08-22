/*
  Test for waiting on updateable
 */
import sys;

// SKIP-THIS-TEST

main {
    updateable_float counter = 0;
    int limit = toint(argv("limit"));
    float limitf = tofloat(argv("limit"));
    foreach i in [1:limit] {
        counter <incr> := 1;
    }

    wait (counter) {
        // This should run after counter is finalized
        assertEqual(counter, limitf, "counter == limit");
    }
}
