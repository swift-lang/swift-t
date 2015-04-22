
import assert;

main
{
  int a = 1;
  int b = 2;
  int c = 3;
  int x;
  int y;
  int z;

  x = a + b;
  y = a*c+b-a+1;
  z = b * (a + 2);

  trace(x,y,z);

  assertEqual(x, 3, "x");
  assertEqual(y, 5, "y");
  assertEqual(z, 6, "z");
}
