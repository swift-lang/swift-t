// THIS-TEST-SHOULD-NOT-COMPILE 

<T>
(T out) f (T arr[], int i) "package" "0.0.0" "f";

main {
  float A[] = [1, 2.0, 3];
  int x = f(A, 1);
  trace(x);
}
