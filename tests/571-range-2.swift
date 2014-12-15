import assert;
import io;

main {

  float x[];

  x = [1.0:2.0];
  assertEqual(x[0], 1.0, "x[0]");
  assertEqual(x[1], 2.0, "x[1]");
  assertEqual(size(x), 2, "len(x)");

  trace("x", repr(x));

  foreach y in [1.0:10.0] {
    printf("y=%f", y);
  }
  
  
  foreach z in [0:1004.9:7.5] {
    printf("z=%f", z);
  }

  foreach a in [0:0.0:0.001] {
    printf("a=%f", a);
  }
  
  foreach b in [0:-1.0] {
    assert(false, "Range loop should not run");
  }
}
