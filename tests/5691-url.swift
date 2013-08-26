
// THIS-TEST-SHOULD-NOT-COMPILE
// Trying to use url as file
import files;

main {
  url f = input_url("ftp://host/path/file");
  trace(read(f));
}
