import assert;

(int o) f (int i) {
  o = i;
}
main {
  // Iterate over fixed range using both loop vars
  foreach i, j in [2:5] {
    trace("loop1", i, j);
    assert(i >= 2 && i <= 5, "loop1 range");
  }
  
  foreach i, j in [f(2):f(5)] {
    trace("loop2", i, j);
    assert(i >= 2 && i <= 5, "loop2 range");
  }
  
  foreach i, j in [1:1] {
    trace("loop3", i, j);
    assert(i == 1, "loop3 range");
  }
}
