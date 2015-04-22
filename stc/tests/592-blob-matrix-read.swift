
#include <builtins.swift>
#include <blob.swift>
#include <matrix.swift>
#include <sys.swift>

main
{
  string home = getenv("HOME");
  string name = "592.data";
  file f = input(name);
  blob b = blob_read(f);
  float A[][] = matrix_from_blob_fortran(b, 3, 3);
  matrix_print(A, 3);
}
