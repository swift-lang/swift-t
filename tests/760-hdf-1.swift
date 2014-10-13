
// Cf. 594-blob-write

// SKIP-THIS-TEST uses HDF

import blob;
import io;

main
{
  float A[] = [ 0.2, 0.3, 0.4, 0.5 ];
  blob b = blob_from_floats(A);
  file f<"A.hdf"> = blob_hdf_write("entry", b);
}
