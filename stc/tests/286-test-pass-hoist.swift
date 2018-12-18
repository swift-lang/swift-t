import assert;
import blob;
import string;

/* Regression test to make sure that hoisting doesn't try to invalidly
   pass an unpassable variable type: like a blob value */


// Pure blob creation function: this should be tempting
// to hoist
@pure
(blob o) make_blob() "turbine" "0.0.1" [
    "set <<o>> [ adlb::string2blob hello ]"
];

// hello is 6 bytes including null terminator
global const int TEST_BLOB_SIZE = 6;

// This should not be hoistable: not pure
(int o) blob_size2(blob i) "turbine" "0.0.1" [
    "set <<o>> [ lindex <<i>> 1 ]"
];

main {
  foreach i in [1:1000] {
    // Should be hoistable
    blob x = make_blob();
    // Should not be hoistable
    int len = blob_size2(x);

    assertEqual(len, TEST_BLOB_SIZE, sprintf("len %i", i));

    f(i);
  }
}

f (int i) {
  // Should be hoistable
  blob x = make_blob();
  // Should not be hoistable
  int len = blob_size2(x);

  assertEqual(len, TEST_BLOB_SIZE, sprintf("in f: len %i", i));
}
