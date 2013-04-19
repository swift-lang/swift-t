
@pure
(int x, int y) doubleresult() {
  x = 1;
  y = 1;
}

main {
  int a, b;
  a, b = doubleresult();
  // Regression test for dead code elim where only one result is used.
  trace(a);
}
