import blob;
import io;

main {
  file data = input_file("593-blob-read.data");
  blob b = blob_read(data);
  float v[] = floats_from_blob(b);
  printf("size(v) = %i", size(v));
  printf("v[0]=%0.2f", v[0]);
  printf("v[last]=%0.2f", v[size(v)-1]);
}
