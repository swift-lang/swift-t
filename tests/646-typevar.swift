// THIS-TEST-SHOULD-NOT-COMPILE

<T>
(T out1, T out2) f (T i) "package" "0.0.0" "f";

main {
  float x;
  int y;
  x, y = f(1);
  trace(x, y);
}


