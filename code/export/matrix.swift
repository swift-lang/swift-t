
#ifndef MATRIX_SWIFT

import io;
import string;

@pure
(float A[][]) matrix_from_blob_fortran(blob b, int m, int n)
"turbine" "0.0.2" 
  [ "set <<A>> [ turbine::matrix_from_blob_fortran_impl <<b>> <<m>> <<n>> ] " ];

(void v)
vector_print(float x[])
{
  r = size(x);
  int i;
  for (i = 0; i < r; i = i+1)
  {
    printf("%i: %0.4f", i, x[i]);
  }
  wait(i) { v = make_void(); }
}

(void v)
vector_print_integer(int x[])
{
  r = size(x);
  int i;
  for (i = 0; i < r; i = i+1)
  {
    printf("%i: %i", i, x[i]);
  }
  wait(i) { v = make_void(); }
}

(void v)
matrix_print(float A[][], int rows)
{
  int i;
  printf("matrix rows: %i", rows) =>
  for (i = 0; i < rows; i = i+1)
  {
    string s;
    for (s = "", int j = 0; j < size(A[i]);
           j = j+1,
           s = sprintf("%s %0.4f", s, A[i][j]))
    {}
    printf("row %i: %s", i, s);
  }
  wait(i) { v = make_void(); }
}

matrix_print_integer(int A[][], int rows)
{
  printf("matrix rows: %i", rows);
  foreach i in [0:rows-1]
  {
    string s;
    for (s = "", int j = 0; j < size(A[i]);
           j = j+1,
           s = sprintf("%s %i", s, A[i][j]))
    {}
    printf("row %i: %s", i, s);
  }
}

#endif
