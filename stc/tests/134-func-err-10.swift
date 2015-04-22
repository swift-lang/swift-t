type x {
  int a;
  int b;
}
import assert;
main {
  // Check can define variable with same name as type
  int x = 3;

  assertEqual(x, 3, "x");
}
