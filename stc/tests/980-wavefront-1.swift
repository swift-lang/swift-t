
/*
   Wavefront pattern
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

  foreach i in [1:N-1]
  {
    foreach j in [1:N-1]
    {
      {
        A[i][j] = f(A[i-1][j-1],A[i-1][j],A[i][j-1]);
      }
    }
  }
  printf("final value: %f", A[N-1][N-1]);

  matrix_print(A, N);
}

(float r) f(float a, float b, float c)
{
  r = a + b + c;
}

matrix_print(float A[][], int n)
{
  foreach i in [0:n-1]
  {
    printf("n: %i", n);
    string s;
    for (s = "", int j = 0; j < n;
           j = j+1,
           s = sprintf("%s %0.4f", s, A[i][j]))
    {}
    printf("%s", s);
  }
}
