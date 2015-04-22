
// COMPILE-ONLY-TEST
// Regression test for bug passing updateablefloats
main {
  updateable_float x = 1.0;

  f(x);
}

f(updateable_float x) {
  trace(x);
}
