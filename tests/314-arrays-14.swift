import assert;
/// Check that empty array constructors work right.
main {
  int B[][] = [[]];
  assertEqual(size(B), 1, "size(B)");
  assertEqual(size(B[0]), 0, "size(B[0])");
}
