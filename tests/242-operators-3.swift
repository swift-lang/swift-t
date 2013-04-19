
import assert;

main
{
  boolean a = true;
  boolean b = true;
  boolean x;
  boolean y;

  // test the ! operator
  x = !a || b;
  y = !a && b;

  trace(x, y);
  assertEqual(x, true, "x");
  assertEqual(y, false, "y");
}
