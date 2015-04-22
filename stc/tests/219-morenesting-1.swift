// Make sure that the flattening optimisation works
main
{
  int a;
  a = 1;
  int c;
  c = 1;
  if (a) {
    {
      int d = 2;
      if (c) {
        {
          int b;
          b = 1;
          trace(b, d);
        }
      }
      trace(d);
    }
    {
      int b;
      int d = 4;
      b = 1;
      trace(b, d);
    }
  }
  trace(c);
}
