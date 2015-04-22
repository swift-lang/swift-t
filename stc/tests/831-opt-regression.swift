import io;

/* Regression test for compile error */
// COMPILE-ONLY-TEST

@pure @dispatch=WORKER
(float o) my_log (float x, float base) "turbine" "0.7.0" [
  "set <<o>> [ expr {log(<<x>>)/log(<<base>>)} ]"
];

printf("log10(100) = " + my_log(100, 10));
