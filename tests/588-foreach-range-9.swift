#include <builtins.swift>
#include <assert.swift>
main {
  int A[];
  @splitdegree=2
  foreach i in [1:100] {
    A[i] = i;
  }
  assertEqual(A[50], 50, "50");
  assertEqual(A[100], 100, "100");
  assertEqual(A[1], 1, "1");
}
