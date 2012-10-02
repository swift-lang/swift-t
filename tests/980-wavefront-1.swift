
/*
   Wavefront pattern
   SKIP-THIS-TEST : blocked on issue #282
*/

#include <io.swift>

main
{
  printf("WAVEFRONT");
  int N = toint(argv("N"));
  float A[][];

  A[0][0] = 0;
  // foreach i in [0:N-1]
  // {
  //   A[i][0] = i;
  //   A[0][i] = i;
  // }

  // foreach i in [1:N-1]
  // {
  //   foreach j in [1:N-1]
  //   {
  //     {
  //       A[i][j] = f(A[i-1][j-1],A[i-1][j],A[i][j-1]);
  //     }
  //   }
  // }
  // printf("final value: %f", A[N-1][N-1]);
}

(float r) f(float a, float b, float c)
{
  r = a + b + c;
}
