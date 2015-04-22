import assert;
// Regression test for sync optimization error

(int o) f( int i) {
  o = i;
}

main {
  int x = f(1);
  int y = f(1);
  int z;
  g(x,y,z);

  z = f(2);
}

@sync
g(int x, int y, int z) {
  wait(x,y,z) {
    trace(x,y,z);
  }
  assertEqual(x,1,"x");
  assertEqual(y,1,"y");
  assertEqual(z,2,"z");
}
