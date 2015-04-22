
/*
  SKIP-THIS-TEST
  Does not work- array index depth issue
*/

import io;
import string;
import stats;

(int x) g(int A) "funcs" "0.0"
[ "set <<x>> [ f_983 <<A>> ]" ];

(int x[]) f(int A[], int n)
{
  int t = sum_integer(A);
  foreach i in [0:n-1]
  {
    x[i] = t;
  }
}

main
{
  int B[][][];
  int N = 4;

  foreach j in [0:N-1]
  {
    foreach k in [0:N-1]
    {
      B[0][j][k] = 0;
    }
  }

  foreach i in [1:N-1]
  {
    foreach j in [0:N-1]
    {
      B[i][j] = f(B[i-1][j]);
    }
  }

  matrix_print(B, N);
}

matrix_print(int A[][], int n)
{
  foreach i in [0:n-1]
  {
    printf("n: %i", n);
    string s;
    for (s = "", int j = 0; j < n;
           j = j+1,
           s = sprintf("%s %i", s, A[i][j]))
    {}
    printf("row: %i %s", i, s);
  }
}
