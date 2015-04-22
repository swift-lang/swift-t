
app (file o) s(int i)
{
  "sleep" i;
}

main
{
  int k;
  k = 2;
  wait (s(k)) {
    trace("DONE");
  }
}
