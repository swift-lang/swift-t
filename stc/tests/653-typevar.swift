// Regression test for compiler error where we can't generate wrapper
// for type variable.  Will need to generate wrapper on demand.

<T> puts (T t) "turbine" "0.0.0" [
  "puts <<t>>"
];

puts2 (int|float|string t) "turbine" "0.0.0" [
  "puts <<t>>"
];

main () {
  puts("Hello world!");
  puts2("Test!");
  puts2(1234);
  puts2(1234.0);
}
