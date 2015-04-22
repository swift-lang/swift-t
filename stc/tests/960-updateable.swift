
import assert;

// Fails at these optimization levels (need to investigate):
// SKIP-O0-TEST

main {
    updateable_float x = inf;

    assertEqual(x, inf, "init") => {
      x <min> := 100.0;
      assertEqual(x, 100.0, "successful update") => {
        x <min> := 125.0;
        assertEqual(x, 100.0, "unsuccessful update");
      }
    }

    updateable_float y = 1;

    y <incr> := 1;
    assertEqual(y, 2.0, "incr 1") => {
      y <incr> := 200;
      assertEqual(y, 202.0, "incr 2") => {
        y <incr> := -10;
        assertEqual(y, 192.0, "incr 3");
      }
    }

    updateable_float z = 1;
    z <scale> := 1;
    assertEqual(z, 1.0, "scale 1") => {
      z <scale> := 200;
      assertEqual(z, 200.0, "scale 2") => {
        z <scale> := 0.5;
        assertEqual(z, 100.0, "scale 3");
      }
    }
}
