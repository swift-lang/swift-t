
// SKIP-THIS-TEST : issue 758

import assert;
import sys;

main {
    updateable_float x = 123;
    float y = x + 321;
    assertEqual(y, 444, "adding updateable and future");
    wait (y) {
      x <incr> := 1;
      // Wait to make sure above increment occurs
      wait (x) {
          assertEqual(x, 124.0, "incremented x");
          float z = x + 100;
          assertEqual(z, 224.0, "z");
      }
    }
}
