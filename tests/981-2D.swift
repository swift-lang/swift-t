
// Related to RDCEP work

import io;

(int r[]) f()
{
  r[0] = 20;
  r[1] = 21;
  r[2] = 22;
}

main
{
  int A[][];
  A[0][0] = 1;
  int m[] = f();
  foreach v, i in m
  {
    printf("i: %i", i);
    if (i != 0)
    {
      A[0][i] = m[i];
    }
  }
}
