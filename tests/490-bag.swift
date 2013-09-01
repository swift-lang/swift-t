// SKIP-THIS-TEST

main {
  bag<int> b;
  
  b += 1;

  f(b);
}


() f (bag<int> X) {
  foreach x in X {
    trace(x);
  }
}
