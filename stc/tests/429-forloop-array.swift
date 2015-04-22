import assert;


main {
  int A[];
  int init[] = [1,2,3];
  for (A = init, int i = 0; i < 10; i = i + 1, A = A2) {
    int A2[] = [A[0] + 1, A[1] + 1, A[2] + 1];
  }
  
  trace(A[0], A[1], A[2]);
  assertEqual(A[0], 11, "A[0]");
  assertEqual(A[1], 12, "A[1]");
  assertEqual(A[2], 13, "A[2]");
}
