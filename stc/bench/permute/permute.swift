
/**
   Possible benchmark
*/

#include <builtins.swift>

#include <io.swift>

(int r) pow(int n, int d)
{
  if (d == 0)
  {
    r = 1;
  }
  else if (d == 1)
  {
    r = n;
  }
  else
  {
    r = pow(n*n, d-1);
  }
}

(int P[][]) permute(int n, int d)
{
  int C = pow(n,d);
  printf("C: %i", C);

  foreach i in [0:C-1]
  {
    int T[];
    for (int j = 0, int t = i; j < d; j = j+1, t = t%/n)
    {
      int m = t %% n;
      T[j] = m;
      printf("m: %i %i %i", i, j, m);
    }
    P[i] = T;
  }
}

main
{
  int P[][];

  int n = 3;
  int d = 2;

  P = permute(n, d);
}
