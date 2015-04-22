import assert;

// Test auto-declaration of variables
main {
  x = 1;
  trace(x);
  y = x + 2;
  A[0][1] = x;
  A[1][0] = y;
  assertEqual(A[0][1], 1, "A[0][1]");
  assertEqual(A[1][0], 3, "A[1][0]");

  // Check type inference for array lookups
  q = A[0];
  r = q[1];
  assertEqual(r, 1, "r");

  // Check whole array creation
  B = [1,2,3];

  z = 2.0;
  trace(z + 1.0);

  a = "hello";
  b = "world";
  c = a + " " + b;
  trace(c);
  assertEqual(c, "hello world", "hello world");

}
