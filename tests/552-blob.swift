import blob;
import assert;

(int o) f (int i) {
  o = i;
}

main {
    string s = "12345";
    blob b = blob_from_string(s);
    assertEqual(blob_size(b), 6, "blob_size");
    // Check that we don't try to pass blob value asynchronously
    if (f(1) == 1) {
      assertEqual(blob_size(b) + 1, 7, "blob_size + 1");
    }
}

