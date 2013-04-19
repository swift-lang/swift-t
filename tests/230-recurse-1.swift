
(int o) f(int i)
{
  int j;
  int n;
  n = 1;
  (j) = i - n;
  if (j)
  {
    (o) = f(j);
  }
  else
  {
    // added branch to avoid variable error
    (o) = 0;
  }
}

main
{
  int z;
  int y;

  z = 3;
  (y) = f(z);
  trace(y);
}
