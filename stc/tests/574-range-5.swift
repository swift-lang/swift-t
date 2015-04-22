// Regression test for reference counting of updateables

import assert;
import math;
main {
  updateable_float saw0 = 0;

  foreach i in [0:0.5:1.0] {
    trace(i) =>
    if (abs_float(i - 0) < 1e-10) {
      trace("incr") =>
      saw0 <incr> := 1;
    }
  }

  wait (saw0) {
    assertEqual(saw0, 1, "Expected to see 1000");
  }
}
