
#ifndef MATRIX_SWIFT

#include <io.swift>
#include <string.swift>

@pure
(float A[][]) matrix_from_blob_fortran(blob b, int m, int n)
"turbine" "0.0.2" "matrix_from_blob_fortran";

matrix_print(float A[][], int rows)
{
  foreach i in [0:rows-1]
  {
    printf("matrix rows: %i", rows);
    string s;
    for (s = "", int j = 0; j < rows;
           j = j+1,
           s = sprintf("%s %0.4f", s, A[i][j]))
    {}
    printf("row %i: %s", i, s);
  }
}

#endif
