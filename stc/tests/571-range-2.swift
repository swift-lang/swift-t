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

  y_range = [1.0:10.0];
  assertEqual(size(y_range), 10, "size(y)");
  assertEqual(y_range[0], 1.0, "y[0]");
  assertEqual(y_range[9], 10.0, "y[9]");
  
  foreach z in [0:1004.9:7.5] {
    printf("z=%f", z);
  }
  
  z_range = [0:1004.9:7.5];
  assertEqual(size(z_range), 134, "size(z)");
  assertEqual(z_range[0], 0.0, "z[0]");
  assertEqual(z_range[20], 150.0, "z[20]");

  foreach a in [95.5:95.51:0.1] {
    printf("a=%f", a);
  }
  
  a_range = [95.5:95.51:0.1];
  assertEqual(size(a_range), 1, "size(a)");
  assertEqual(a_range[0], 95.5, "a[0]");
  
  foreach b in [0:-1.0] {
    assert(false, "Range loop should not run");
  }
  
  b_range = [0:-1.0];
  assertEqual(size(b_range), 0, "size(b)");

}
