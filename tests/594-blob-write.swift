
import blob;
import io;

main
{
  float A[] = [0.2, 0.3, 0.4, 0.5];
  blob b = blob_from_floats(A);
  file f<"A.blob"> = blob_write(b);
}
