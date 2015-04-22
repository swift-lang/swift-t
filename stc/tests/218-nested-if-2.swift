

(int r) f () {
  int a;
  a = 1;
  int b = 3;

  if (a + 4 - (b + a))
  {
    int c;
    if (b) {
      c = b + 2;
      r = c;
    } else {
      c = 3;
      r = 4;
    }
  } else {
    int c;
    c = 1;
    r = c + 1;
    
  }
}

main
{
  int x;
  x = f();
  trace(x);
}
