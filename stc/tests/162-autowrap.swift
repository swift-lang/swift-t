import assert;
import blob;

// Test the auto-wrapping functionality with blobs

// blob input
@pure 
(int o) blob_size2(blob i) "turbine" "0.0.1" [ 
    "set <<o>> [ lindex <<i>> 1 ]"
];

// blob output
@pure
(blob o) make_blob() "turbine" "0.0.1" [
    "set <<o>> [ adlb::blob_from_string hello ]"
];

main {
  int size1 = blob_size(blob_from_string("test"));
  int size2 = blob_size2(blob_from_string("test"));
  trace(size1, size2);
  assertEqual(size1, size2, "sizes equal");

  blob b = make_blob();
  string s = string_from_blob(b);
  trace("s=" + s);
  assertEqual(s, "hello", "string contents");
}
