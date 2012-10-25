#include <builtins.swift>
#include <blob.swift>
#include <assert.swift>

main {
    string s = "12345";
    blob b = blob_from_string(s);
    assertEqual(blob_size(b), 6, "blob_size");
    assertEqual(blob_size(b) + 1, 7, "blob_size + 1");
    
    
    // Don't allow blob_from_string to be inlined
    blob b2 = s2b(s);
    assertEqual(blob_size(b2), 6, "blob_size :: 2");
    assertEqual(blob_size(b2) + 1, 7, "blob_size + 1 :: 2");
}

(blob b) s2b (string s) {
  b = blob_from_string(s);
}
