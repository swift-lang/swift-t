
// SKIP-THIS-TEST : issue 758

import assert;
import sys;

main {
    updateable_float x = 123;
    float y = x + 321;
    assertEqual(y, 444, "adding updateable and future");
    wait (y) {
      x <incr> := 1;
      // Sleep to make sure above increment occurs
      void signal = sleep(1);
      wait (signal) {
          assertEqual(x, 124, "incremented x");
          float z = x + 100;
          assertEqual(z, 224, "z");
      }
    }
}
