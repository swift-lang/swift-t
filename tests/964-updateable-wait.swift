/*
  Test for waiting on updateable
 */
import sys;
import assert;
import math;

main {
    updateable_float counter = 0;
    int limit = toint(argv("limit"));

    wait (counter) {
        // This should run after counter is finalized
        assertEqual(toInt(counter), limit, "counter == limit");
    }
    wait (sleep(0.1)) { 
        foreach i in [1:limit] {
            counter <incr> := 1;
        }
    }
}
