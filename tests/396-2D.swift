
// Currently fails

#include <builtins.swift>
#include <io.swift>

(int r[]) f()
{
  r[0] = 2;
}

main
{
  int A[][];
  A[0][0] = 1;
  int t[] = f();
  foreach i, v in t
  {
    printf("i: %i", i);
    A[0][i] = t[i];
  }
}
