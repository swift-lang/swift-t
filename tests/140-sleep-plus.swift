
(int o) sub_a(int a, int b)
{
  (o) = a + b;
}

(int o, int u) sub_b(int a, int b, int c, int d)
{
  int k;
  // UNSET-VARIABLE-EXPECTED
  int m;
  k = 2;
  (o) = a + b;
  (u) = c + d;
}

main
{
  int a;
  int b;
  int c;
  int d;
  int e;
  int f;

  a = 5;
  b = 5;
  (c) = sub_a(a,b);
  d = 5;
  (e,f) = sub_b(a,b,c,d);
}
