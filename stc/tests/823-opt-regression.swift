import assert;


main {
  // Call to avoid test_fn being optimized out
  float z = test_fn();

  assertEqual(z, 1.0, "z");
}

(float x, float y) run() "turbine" "0.0.1" [
   "lassign [ list 0.0 1.0 ] <<x>> <<y>>"
];

(float z) test_fn () {
  // Get an opaque result
  float x, y;
  x, y = run();

  // wait for the results before continuing;
  wait (x, y) {
    if (x > y) {
      z = x;
    } else {
      z = y;
    }
  }
}

