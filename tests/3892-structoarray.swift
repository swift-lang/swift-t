// Regression test - copying struct with array inside does not work correctly
// SKIP-THIS-TEST
// Issue #760 
import assert;

type inner {
  int x[];
}

type outer {
  inner x;
}

main {
  inner x;
  x.x = [1, 2, 3];
  
  outer y;
  y.x = x;

  assertEqual(y.x.x[0], 1, "1");
  assertEqual(y.x.x[1], 2, "2");
  assertEqual(y.x.x[2], 3, "3");
}
