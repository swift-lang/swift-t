// Test checkpointing for data structures
// SKIP-THIS-TEST

import assert;
import math;
import stats;
import blob;

main {

  int a;
  float A[];
  bag<int> abag;

  a, A, abag = arrayf(10, [blob_from_string("hello"),
                           blob_from_string("world")]);
  assertEqual(a, 10 + 6, "a");
  assertEqual(A[0], 6.0, "A[0]");
  assertEqual(bag_size(abag), 2, "bag_size(abag)");
}


@checkpoint
(int a, float A[], bag<int> abag) arrayf (int b, blob B[]) {

  foreach x, i in B {
    A[i] = itof(blob_size(x));
    abag += blob_size(x);
  }
  a = b + round(sum_float(A)/itof(size(A)));
}
