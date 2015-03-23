/*
 * Regression test for bad hoisting of subexpression out of loop
 */

int x;

foreach i in [1:100] {
  // Optimiser hoists y declaration but not calculation
  int y = x * x;
  trace(y);
}


(int o) id (int i) "turbine" "0.0" [
    "set <<o>> <<i>>"
];

// Hide value from optimiser
x = id(1);
