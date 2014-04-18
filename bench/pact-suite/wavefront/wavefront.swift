
/*
   Wavefront pattern
*/

import io;
import string;
import sys;
import math;

main
{
  int N = toint(argv("N"));
  float mu = tofloat(argv("mu"));
  float sigma = tofloat(argv("sigma"));
  printf("WAVEFRONT N=%d mu=%f sigma=%f", N, mu, sigma);
  float A[][];

  A[0][0] = 0;
  foreach i in [1:N-1]
  {
    A[i][0] = itof(i);
    A[0][i] = itof(i);
  }

  foreach i in [1:N-1]
  {
    foreach j in [1:N-1]
    {
      a, b, c = A[i-1][j-1], A[i-1][j], A[i][j-1];
      /*A[i][j] = h(f(g(a, mu, sigma), mu, sigma),
                  f(g(b, mu, sigma), mu, sigma),
                  f(g(c, mu, sigma), mu, sigma));*/
      A[i][j]=work(a, b, c, mu, sigma);
    }
  }
  printf("result N: %i value: %.0f", N, A[N-1][N-1]);

}

/*
(float r) f(float a, float b, float c)
{
  // TODO: work function?
  r = a + b + c;
} */

@dispatch=WORKER
(float v) f(float i, float mu, float sigma) "lognorm_task" "0.0.0" [
  "set <<v>> [ lognorm_task::lognorm_task_impl <<i>> 0 <<mu>> <<sigma>> ] "
];

@dispatch=WORKER
(float v) g(float i, float mu, float sigma) "lognorm_task" "0.0.0" [
  "set <<v>> [ lognorm_task::lognorm_task_impl <<i>> 0 <<mu>> <<sigma>> ] "
];

(float v) h(float x, float y, float z) {
  v = sqrt(x + y + z);
}

@dispatch=WORKER
(float v) work(float i, float j, float k, float mu, float sigma) "lognorm_task" "0.0.0" [
  "set <<v>> [ expr sqrt(<<i>> + <<j>> + <<k>>) + 1 ]; lognorm_task::lognorm_task_impl <<i>> <<j>> <<mu>> <<sigma>> "
];
