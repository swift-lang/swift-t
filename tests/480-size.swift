
import assert;

main
{
  int x[] = [1:15];
  assertEqual(15, size(x), "size(x)");

  int y[] = [-1:12:2];
  assertEqual(7, size(y), "size(y)");
}
