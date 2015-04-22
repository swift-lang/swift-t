import assert;
import math;

// Test for accumulated rounding errors that would occur
// with some implementations of floating point ranges
main {

  // 0.1 doesn't have exact binary floating point representation
  // Cumulative rounding error for 10000 additions is likely
  // measurable
  A = [0.0:1000:0.1];
  assertEqual(size(A), 10001, "size(A)") =>
  assertEqual(A[10000], 1000, "A[10000]");

  updateable_float count = 0;
  updateable_float saw1000 = 0;

  foreach i in [0.0:1000:0.1] {
    
    count <incr> := 1;
    if (abs_float(i - 1000) < 1e-10) {
      saw1000 <incr> := 1;
    }
  }

  wait (count, saw1000) {
    assert(saw1000 == 1, "Expected to see 1000");
    assert(toInt(count) == 10001, "Expected count 10001");

  }
}
