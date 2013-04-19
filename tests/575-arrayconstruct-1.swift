
import assert;
import stats;
import random;

main {
    int A[] = [1,2,3];

    assertEqual(sum_integer(A), 6, "sum of A");
    assertEqual(sum_integer([4,5,6]), 15, "sum of [4,5,6]");
    trace(A[1]);
    trace("Size of A: ", size(A));
    trace(size(A) + randint(0, 10)); // Add to runtime value
    wait (A) {
      trace("A closed!");
    }
}
