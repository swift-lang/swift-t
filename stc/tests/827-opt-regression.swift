// Regression test for reference counting bug


main () {

  int A[];
  
  // Reference count of A drops to zero prematurely
  A = f([0,1,2,3], 0);

  trace(A[0], A[1], A[2]);
}

(int A[]) f (int init_array[], int z) {
  wait (z) {
    for (int i = 0, A=init_array;
            i < 10;
            i = i + 1, A = B) {
      int B[] = A;
    }
  }
}
