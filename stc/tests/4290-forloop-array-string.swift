
// Check that string operations work ok in for loop

#include <builtins.swift>
#include <string.swift>

main {
  int N = 4;
  float A[][];
  foreach j1 in [0:N-1] {
    foreach j2 in [0:N-2] {
      A[j1][j2] = itof(j1) + itof(j2);
    }}

  for (string s = "", int i = 0; i < N-2;
       i = i + 1,
       s = sprintf("%s %f", s, A[0][i]))
  {
    trace(i,s);
  }
}
