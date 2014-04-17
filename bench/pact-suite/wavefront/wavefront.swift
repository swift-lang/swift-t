
/*
   Wavefront pattern
*/

#include <builtins.swift>
#include <io.swift>
#include <mpe.swift>
#include <string.swift>
#include <sys.swift>

main
{
  int N = toint(argv("N"));
  float sleeptime = tofloat(argv("sleeptime"));
  printf("WAVEFRONT N=%d sleeptime=%f", N, sleeptime);
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
      A[i][j] = h(f(g(a)), f(g(b)), f(g(c)));
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
(float v) f(float i, float j, float k, float seconds) "turbine" "0.0.4" [
  "set <<v>> [ expr <<i>> + <<j>> + <<k>> ] ; if { <<seconds>> > 0 } { turbine::spin <<seconds>> }"
];
