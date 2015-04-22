
import io;
import assert;
import math;
import stats;

main {
    testMultiAgg();
}

() testMultiAgg () {
    float samples1[] = [itof(fib(0)), itof(fib(1))];
    float samples2[] = [itof(fib(2)), itof(fib(10)), itof(fib(11)), itof(fib(3)),
                         itof(fib(4)), itof(fib(7)),
                         itof(fib(8)), itof(fib(9))];
    float samples3[] = [itof(fib(14)), itof(fib(13))];
    float samples4[] = [itof(fib(12)), itof(fib(5)), itof(fib(6))];

    PartialStats ps1;
    PartialStats ps2;
    PartialStats ps3;
    PartialStats ps4;
    ps1.n, ps1.mean, ps1.M2 = statagg(samples1);
    ps2.n, ps2.mean, ps2.M2 = statagg(samples2);
    ps3.n, ps3.mean, ps3.M2 = statagg(samples3);
    ps4.n, ps4.mean, ps4.M2 = statagg(samples4);

    foreach ps, i in [ps1, ps2, ps3, ps4] {
      printf("Partial %i: n=%f mean=%f M2=%f std=%f",
                i, ps.n, ps.mean, ps.M2, sqrt(ps.M2 / itof(ps.n)));
    }

    int n; float mean; float stdev;

    n, mean, stdev = stat_combine([ps1, ps2, ps3, ps4]);
    // Expected values calculated with 950-calc.py
    assertEqual(n, 15, "n");
    assertLT(abs_float(mean - 65.733333333333), 0.00001, "mean");
    // M2 = variance * n
    // TODO: this is a loose bound
    assertLT(abs_float(stdev - 104.93074965), 0.1, "std");
    trace("fib results 2",n, mean, stdev);

}



(int o) fib(int n)
{
  switch (n) {
    case 0:
      // fib_0 = 0
      o = 0;
    case 1:
      // fib_1 = 1
      o = 1;
    default:
      o = fib(n - 1) + fib(n - 2);
  }
}
