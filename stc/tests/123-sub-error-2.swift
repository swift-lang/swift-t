// THIS-TEST-SHOULD-NOT-COMPILE
(int x, int y) f () {
    x = 1;
    y = 2;
}

main {
  int x;
  int y;
  int z = 1;
  (x, y) = f(z);
  if (x != 0) {
    trace(y);
  } else {
    trace(z);
  }
}
