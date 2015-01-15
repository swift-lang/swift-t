int a;
int branch = f(1);
if (branch == 1) {
  a = 1;
} else {
  if (branch == 2) {
    a = 2;
  } else {
    a = 3;
  }
}
trace("RESULT: " + fromint(a));


// Opaque function that can't be inlined
(int o) test (int i) "turbine" "0.0.1" [
  "set <<o>> <<i>>"
];

(int o) f ( int i ) {
  o = test(i);
}
