
// Recursive multiplication based on addition, subtraction

import assert;

// mult(i,0,s) = s
// mult(i,j,s) = mult(i,j-1,s+i)
(int o) my_mult_helper(int i, int j, int s)
{
  int t;
  int n;
  n = 1;
  t = s + i;
  if (j)
  {
    int k;
    k = j - n;
    o = my_mult_helper(i,k,t);
  }
  else
  {
    o = s;
  }
}

// Reserve multiply() for actual multiplication function
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
  assertEqual(z, x*y,"");
}
