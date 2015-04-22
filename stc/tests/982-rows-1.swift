
import io;
import string;
import stats;

(int x) f(int A[])
{
  x = sum_integer(A);
}

main
{
  int B[][];
  int N = 4;
  foreach j in [0:N-1]
  {
    B[0][j] = 0;
  }

  foreach i in [1:N-1]
  {
    foreach j in [0:N-1]
    {
      B[i][j] = j + f(B[i-1]);
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
