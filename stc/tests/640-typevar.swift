// COMPILE-ONLY-TEST 

<T>
(T x) f (T y) "package" "0.0.0" "f";

main {
  int x = f(1);
  trace(x);
}
