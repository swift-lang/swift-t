// THIS-TEST-SHOULD-NOT-COMPILE

int x;

f() {
  // Globals are read-only in functions
  x = 2;
}
