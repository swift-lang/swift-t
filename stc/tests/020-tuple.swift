import assert;

main {
  x, y = (2, 3);

  assertEqual(x, 2, "x");
  assertEqual(y, 3, "y");

  a, b, c = (x + y, x - y, x * y);
  assertEqual(a, 5, "a");
  assertEqual(b, -1, "b");
  assertEqual(c, 6, "c");

  q = (x);
  assertEqual(q, x, "q");

}
