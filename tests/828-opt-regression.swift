import assert;
import sys;

/* 
  Regression test for wait coalesce pass with wait rearrange bug.
  The problem is detected by validation in STC... so...
  COMPILE-ONLY-TEST
 */


main () {
  boolean b1 = idb_sleep(0);
  assertEqual(b1, true, "b1");

  boolean b2 = idb_sleep(42);
  assertEqual(b2, false, "b2");
}


(boolean o) idb_sleep (int i) {
    wait (sleep(0.05)) {
        if (i != 0) {
          o = true;
        } else {
          o = false;
        }
    }
}

