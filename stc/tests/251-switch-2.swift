
(int r) f() {
    r = 1;
}

main
{
  int a = 1;
  int b;
  switch (f()) {
    case 1:
        int c;
        c = a + a;
        b = a + 1;
    case 20:
        b = 1;
    case 2000:
        b = 2;
    default:
        b = 2102 + 2420;
  }
  trace(b);
}
