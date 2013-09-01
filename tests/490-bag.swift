// SKIP-THIS-TEST


main {
  bag<int> b;
  f(b);
}


() f (bag<int> X) {
  foreach x in X {
    trace(x);
  }
}
