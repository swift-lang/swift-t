
// Recursive my_multiplication based on addition, subtraction

import assert;

// my_mult(i,0,s) = s
// my_mult(i,j,s) = my_mult(i,j-1,s+i)

(int o) my_mult_helper(int i, int j, int s)
{
  int t;
  int n;
  boolean m;
  n = 1;
  t = s + i;
  if (j)
  {
    int k;
    k = j - n;
    o = my_mult_helper(i,k,t);
  }
  m = j == 0;
  if (m)
  {
    o = s;
  }
}

// Reserve my_multiply() for actual my_multiplication function
(int u) my_mult(int i, int j)
{
  int s;
  s = 0;
  u = my_mult_helper(i,j,s);
}

// Compute z = x*y
main
{
  int x;
  int y;
  int z;

  x = 3;
  y = 3;

  z = my_mult(x,y);
  trace(z);
  assertEqual(z, x*y, "");
}
