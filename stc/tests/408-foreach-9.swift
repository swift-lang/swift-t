import sys;
import stats;
import assert;


main {

  int A[];
  foreach i in [1:id(10)] {
    // Check spawning multiple things in loop - test for reference counting
    void x = sleep_trace(0.01, "test " + fromint(i));
    void y = sleep_trace(0.01, "test2 " + fromint(i));
    sleep(0.02);

    wait (x) {
      A[2*i] = 1;
    }
    wait (y) {
      A[2*i + 1] = 1;
    }

    foreach j in [1:2] {
      trace(i, j);
    }
  }

  assertEqual(sum_integer(A), 20, "sum of A");
}

(int o) id (int i) {
  o = i;
}
