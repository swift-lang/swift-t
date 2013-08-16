
import assert;

main
{
  int x[] = [1:15];
  assertEqual(15, size(x), "size(x)");

  int y[] = [-1:12:2];
  assertEqual(7, size(y), "size(y)");


  assertEqual(0, size([10:1]), "start > end");
  assertEqual(1, size([1:1]), "start == end");
  assertEqual(0, size([1:0]), "start == end + 1");
  
  
  assertEqual(1, size([3:5:3]), "test step 1");
  assertEqual(2, size([3:6:3]), "test step 2");
  assertEqual(2, size([3:7:3]), "test step 3");
}
