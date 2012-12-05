#include <builtins.swift>
#include <assert.swift>
// SKIP-THIS-TEST
/// Check that empty array constructors work right.
main {
  int B[][] = [[]];
  assertEqual(size(B), 1, "size(B)");
  assertEqual(size(B[0]), 0, "size(B[0])");
}
