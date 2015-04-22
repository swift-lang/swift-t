
import blob;

main {
  float A[];
  blob b;
  foreach i in [0:2]
  {
    A[i] = itof(i);
  }
  b = blob_from_floats(A);
}

