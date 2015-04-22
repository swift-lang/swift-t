import blob;
import matrix;

main() {
  // Fortran arrays are column major
  blob b = blob_from_floats([1,2,3,4,5,6,7,8,9]);
  int n = 3;
  matrix_print(matrix_from_blob_fortran(b, n, n), n);
}
