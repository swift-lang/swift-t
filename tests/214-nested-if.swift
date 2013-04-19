
main
{
  int a;
  a = 1;
  int b = 3;

  int d;
  if (a + 4 - (b + a))
  {
    int c;
    c = 1;
    d = c + 1;
  } else {
    
    int c;
    if (b) {
      c = b + 2;
      d = c;
    } else {
      c = 3;
      d = 4;
    }
  }
  trace(d);
}
