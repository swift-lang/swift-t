
#include <builtins.swift>
#include <assert.swift>

main
{
  boolean a = true;
  boolean b = true;
  boolean x;
  boolean y;

  // test the ! operator
  x = !a || !b;
  y = !(x || b);

  trace(x, y);
  assertEqual(x, false, "x");
  assertEqual(y, false, "y");
}
