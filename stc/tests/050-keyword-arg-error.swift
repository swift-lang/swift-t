
// THIS-TEST-SHOULD-NOT-COMPILE

f(int x=0, int y=0) {
  trace(x, y);
}

// Cannot provide keyword arg before positional
f(y=2, 5);
