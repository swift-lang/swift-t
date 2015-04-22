
// Regression test for hoisting
int x;

int a,b;
a,b = f();
wait (a) {
  // Bug is triggered by multiple output function
  // with output vars declared in different scopes
  int y;
  x, y = f();
}
trace(x);

@pure
(int x, int y) f () {
 x = 1;
 y = 1;
}
