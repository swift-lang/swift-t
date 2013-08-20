// THIS-TEST-SHOULD-NOT-COMPILE
// Regression test for compiler internal error where we substitute invalid
// type into typevar 

// NOTE: if we allow passing structs into TCL code directly, will need
// to modify test

type test {
  int a;
  int b;
}

<T> puts (T t) "turbine" "0.0.0" [
  "puts <<t>>"
];

main () {
  test t;
  t.a = 1;
  t.b = 2;
  puts(t);
}
