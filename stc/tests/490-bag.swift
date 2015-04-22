main {
  bag<int> b;
  
  b += 1;
  b += 1;
  b += 2;

  f(b);
}


() f (bag<int> X) {
  foreach x in X {
    trace(x);
  }
}
