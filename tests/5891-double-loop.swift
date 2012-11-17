

#include <builtins.swift>

main
{
  int A[][];

  int rows = 3;
  int columns = 3;
  foreach i in [0:rows-1]
  {
    A[i][0] = 0;
  }
  foreach i in [0:rows-1]
  {
    foreach j in [0:columns-1]
    {
      A[i][j+1] = 1;
    }
  }
}
