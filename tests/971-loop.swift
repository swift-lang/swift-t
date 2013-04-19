import assert;


(int r) f (int x) {
  r = x;
}

main {
  // Test optimisation logic
  int x = f(1);
  int y;
  int z;
  int A[];
 
  // Should be pushed into wait below
  wait (y) {
    A[0] = -1;
    z = 6;
  }
  
  wait(x) {
    A[x] = 2;
    A[x+1] = 5;
    y = x + 2;
    A[z] = 13;
  }

  wait(z) {
    A[z+1] = 27;
  }

  // These should be pushed into wait
  A[y] = 7; 
  A[y+1] = 9; 
  A[y+2] = 12; 


  assertEqual(A[0], -1, "A[0]");
  assertEqual(A[1], 2, "A[1]");
  assertEqual(A[2], 5, "A[2]");
  assertEqual(A[3], 7, "A[3]");
  assertEqual(A[4], 9, "A[4]");
  assertEqual(A[5], 12, "A[5]");
  assertEqual(A[6], 13, "A[6]");
  assertEqual(A[7], 27, "A[7]");
}
