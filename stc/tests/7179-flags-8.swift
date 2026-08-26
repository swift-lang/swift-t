// flags() coexists with formal parameters on main(), which bind the
// positional arguments just as arguments() does

flags(boolean loud, int a = 0);

main (string user, int v) {
  trace("hi " + user, v + a);
  if (loud) {
    trace("LOUD");
  }
}
