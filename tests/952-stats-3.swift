
import io;
import assert;
import math;
import stats;
import random;
import sys;

(PartialStats p) initPartial(float data) {
  p.n = 1;
  p.mean = data;
  p.M2 = 0;
}

main {
  int data[];
  nsamples = 1000;
  int max = 200;
  int min = 100;

  // Large computation
  foreach i in [1:nsamples] {
    // Uniformly distributed
    data[i] = randint(min, max);
  }

  float floatdata[];
  foreach i in [1:nsamples] {
    floatdata[i] = itof(data[i]);
  }

  PartialStats ps[];
  foreach i in [1:nsamples] {
    // Test asynchronicity
    if (random() < 0.5) {
      PartialStats p;
      ps[i] = p;
      wait (sleep(0.002 * random())) {
        // Workaround for bad variable usage analysis
        p2 = initPartial(floatdata[i]);
        p.n = p2.n;
        p.mean = p2.mean;
        p.M2 = p2.M2;
      }
    }
    else
    {
      ps[i] = initPartial(floatdata[i]);
    }
  }

  n, mean, stdev = stat_combine(ps);
  assertEqual(n, nsamples, "n");
  assertLT(abs_float(mean - itof(max + min) / 2), 3, "mean");
  // TODO: stddev check

  printf("n=%i mean=%f std=%f", n, mean, stdev);
}
