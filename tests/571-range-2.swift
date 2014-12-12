import assert;
import io;

main {

  float x[];

  x = [1.0:2.0];
  assertEqual(x[0], 1.0, "x[0]");
  assertEqual(x[1], 2.0, "x[1]");
  assertEqual(size(x), 2, "len(x)");

  trace(repr(x));

  foreach y in [1.0:10.0] {
    printf("y=%f", y);
  }
}
