
import assert;
import math;
import stats;

main {
    testStatAgg();
}

() testStatAgg () {
    float samples[] = [1.0, 2.0, 3.0, 4.0];
    int n; float mean; float M2;

    n, mean, M2 = statagg(samples);
    assertEqual(n, 4, "n");
    assertEqual(mean, 2.5, "mean");
    // M2 = variance * n
    assertLT(abs_float(M2 - 5.0), 0.00000001, "M2");


    // Check it works with delayed computation
    float fibsamples[] = [0.0, itof(fib(1)), itof(fib(5)), itof(fib(6)),
                             itof(fib(7)), itof(fib(8)), itof(fib(2)),
                             itof(fib(3)), itof(fib(4)), itof(fib(9))];
    int fn; float fmean; float fM2;
    fn, fmean, fM2 = statagg(fibsamples);
    assertEqual(fn, 10, "fn");
    // Expected values calculated with 950-calc.py
    assertLT(abs_float(fmean - 8.8), 0.000000001, "fmean");
    // M2 = variance * n
    // TODO: this is a loose bound
    assertLT(abs_float(fM2 - 1095.6), 2.5, "fM2");
    trace("fib results",fn, fmean, fM2);
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
