
import io;
import assert;
import math;
import stats;
import random;

(PartialStats p) initPartial(float data) {
  p.n = 1;
  p.mean = data;
  p.M2 = 0;
}

main {
  int data[];
  nsamples = 10000;
  max = 200;
  min = 100;

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
        p = initPartial(floatdata[i]);
      }
    }
    else
    {
      ps[i] = initPartial(floatdata[i]);
    }
  }

  n, mean, M2 = stat_combine(ps);
  assertEqual(n, nsamples, "n");
  assertLT(abs_float(mean - itof(max + min) / 2), 2, "mean");
  // TODO: stddev check
}
