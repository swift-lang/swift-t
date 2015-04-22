
/*
   Related to RDCEP work
*/

// Skip tests - see issue 628
// SKIP-O0-TEST
// SKIP-O1-TEST

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
      int t[] = f(B[i-1][j], N);
      foreach v, k in t
      {
        B[i][j][k] = t[k];
      }
    }
  }

  matrix_print(B[0], N);
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
