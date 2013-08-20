// Regression test for compiler error where we can't generate wrapper
// for type variable.  Will need to generate wrapper on demand.

// SKIP-THIS-TEST

<T> puts (T t) "turbine" "0.0.0" [
  "puts <<t>>"
];

main () {
  puts("Hello world!");
}
