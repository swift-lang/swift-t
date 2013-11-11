import assert;

// Basic test for checkpointing

main {
 
  trace(f(1));
  trace(f(2));

  foreach i in [1:100] {
    trace(f(i));
    t1, t2 = g(i);
    trace(t1, t2);
    trace(h(i, fromint(i*10), blob_from_string(fromint(i*1000))));
  }


  assertEqual(f(1), 2, "f(1)");

  x1, x2 = g(3);
  assertEqual(x1, 4, "x1");
  assertEqual(x2, 5, "x2");


  // 10 + 10 + 4
  tot = h(10, "word", blob_from_string("word word"));
  assertEqual(tot, 24, "tot");
}


// Single scalar arg
@checkpoint
(int o) f (int i) {
    trace("f executed args: " + fromint(i));
    o = i + 1;
}

// Single scalar arg, multiple outputs
@checkpoint
(int o1, int o2) g (int i) "turbine" "0.0" [
  "puts \"trace: g executed args: <<i>>\"; lassign [ list [ expr <<i>> + 1 ] [ expr <<i>> + 2 ] ] <<o1>> <<o2>>"
];

import blob;
import string;
// Multiple scalar args, including blob
@checkpoint
(int o) h (int i, string s, blob b) {
  trace("h executed args: " + fromint(i));
  o = i + blob_size(b) + strlen(s);
}
