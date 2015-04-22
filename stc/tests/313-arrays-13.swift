import assert;

/// Check that empty array constructors work right.
main {
  int A[] = [];
  assertEqual(size(A), 0, "size(A)");

  int B[][] = [[], []];
  assertEqual(size(B), 2, "size(B)");
  
  int C[][] = [[], [], [1,2,3]];
  assertEqual(size(C), 3, "size(C)");
  assertEqual(C[2][0], 1, "C[2][0]");
}
