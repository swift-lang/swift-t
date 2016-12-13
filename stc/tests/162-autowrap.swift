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
    "set <<o>> [ adlb::string2blob hello ]"
];

main {
  int size1 = blob_size(string2blob("test"));
  int size2 = blob_size2(string2blob("test"));
  trace(size1, size2);
  assertEqual(size1, size2, "sizes equal");

  blob b = make_blob();
  string s = blob2string(b);
  trace("s=" + s);
  assertEqual(s, "hello", "string contents");
}
