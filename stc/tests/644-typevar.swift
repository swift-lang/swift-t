import assert;

main {
  float A[] = [1, 2.0, 3];
  // Test polymorphic container functions
  assertEqual(3, size(A), "size(A)");
  assertEqual(true, contains(A, 0), "contains(A, 0)");
  assertEqual(false, contains(A, 4), "contains(A, 4)");
}

