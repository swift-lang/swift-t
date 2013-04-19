// THIS-TEST-SHOULD-NOT-COMPILE

<T>
(T out1, T out2) f (T arr[], int i) "package" "0.0.0" "f";

main {
  float A[] = [1, 2.0, 3];
  int B[] = [1, 2, 3];
  float x;
  int y;
  x, y = f(A, 1);
  trace(x, y);
}

