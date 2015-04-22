
/*
   Wavefront pattern
   Regression test for bad code generation
*/

import io;
import string;
import sys;

main
{
  printf("WAVEFRONT");
  int N = toint(argv("N"));
  float A[][];

  trace(N);

  A[0][0] = 0;
  foreach i in [1:N-1]
  {
    A[i][0] = itof(i);
    A[0][i] = itof(i);
  }
  
  @leafdegree=1
  foreach i in [1:N-1]
  {
    foreach j in [1:N-1]
    {
      a, b, c = A[i-1][j-1],A[i-1][j],A[i][j-1];
      A[i][j] = f(a, b, c);
    }
  }
  printf("final value: %f", A[N-1][N-1]);

  //matrix_print(A, N);
}

@dispatch=WORKER
(float r) f(float a, float b, float c) "turbine" "0.0" [
  "turbine::spin 0.01; set <<r>> [ expr {<<a>> + <<b>> + <<c>>} ]"
];
