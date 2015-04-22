// COMPILE-ONLY-TEST 

<T>
(T out) f (T arr[], int i) "package" "0.0.0" "f";

main {
  float A[] = [1, 2.0, 3];
  int B[] = [1, 2, 3];
  float x = f(A, 1);
  int y = f(B, 1);
  trace(x, y);
}
