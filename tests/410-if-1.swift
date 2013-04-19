
(int r) f(int x)
{
  r = x;
}

(int r) g(int x)
{
  r = x;
}

(int r) h(int x)
{
  r = x;
}

(int r) j(int x)
{
  r = x;
}

(int a, int b) myfun (int x) {
  if (f(x)) {
     a = h(x);
  } else {
     a = j(x);
  }
  b = g(x);
}

main
{
   int x;
   int a;
   int b;
   x = 4;
   (a, b) = myfun(x);
   trace(a);
}
